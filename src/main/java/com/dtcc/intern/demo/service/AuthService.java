package com.dtcc.intern.demo.service;

import com.dtcc.intern.demo.dto.CreateSupportUserRequest;
import com.dtcc.intern.demo.dto.CreateSupportUserResponse;
import com.dtcc.intern.demo.dto.LoginRequest;
import com.dtcc.intern.demo.dto.LoginResponse;
import com.dtcc.intern.demo.dto.LogoutResponse;
import com.dtcc.intern.demo.dto.RegisterRequest;
import com.dtcc.intern.demo.dto.RegisterResponse;
import com.dtcc.intern.demo.entity.AppUser;
import com.dtcc.intern.demo.entity.Role;
import com.dtcc.intern.demo.entity.TeamService;
import com.dtcc.intern.demo.exception.BadRequestException;
import com.dtcc.intern.demo.exception.ConflictException;
import com.dtcc.intern.demo.exception.UnauthorizedException;
import com.dtcc.intern.demo.repository.AppUserRepository;
import com.dtcc.intern.demo.repository.RoleRepository;
import com.dtcc.intern.demo.repository.TeamServiceRepository;
import com.dtcc.intern.demo.security.AuthenticatedUser;
import com.dtcc.intern.demo.security.JwtService;
import com.dtcc.intern.demo.security.RoleName;
import com.dtcc.intern.demo.security.TokenIdentity;
import com.dtcc.intern.demo.security.TokenRevocationService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;
    private final TeamServiceRepository teamServiceRepository;
    private final TokenRevocationService revocationService;

    public AuthService(AuthenticationManager authenticationManager,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AppUserRepository appUserRepository,
                       RoleRepository roleRepository,
                       TeamServiceRepository teamServiceRepository,
                       TokenRevocationService revocationService) {
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.appUserRepository = appUserRepository;
        this.roleRepository = roleRepository;
        this.teamServiceRepository = teamServiceRepository;
        this.revocationService = revocationService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        AppUser saved = createAccount(request.name(), request.email(), request.password(), RoleName.USER, null);
        return new RegisterResponse(
                saved.getUserId(), saved.getName(), saved.getEmail(), saved.getRole().getRoleName());
    }

    /**
     * Provisions a SUPPORT account on the team the super admin selected. The role is
     * never taken from the request - it is always SUPPORT, so this endpoint can never
     * mint another SUPER_ADMIN. Only a SUPER_ADMIN can reach it, which SecurityConfig
     * enforces. The team is mandatory: an engineer with no team is never eligible for
     * automatic assignment.
     */
    @Transactional
    public CreateSupportUserResponse createSupportUser(CreateSupportUserRequest request) {
        TeamService team = teamServiceRepository.findById(request.teamId())
                .orElseThrow(() -> new BadRequestException(
                        "Unknown team id " + request.teamId()));

        AppUser saved = createAccount(
                request.name(), request.email(), request.password(), RoleName.SUPPORT, team);

        return new CreateSupportUserResponse(
                saved.getUserId(),
                saved.getName(),
                saved.getEmail(),
                saved.getRole().getRoleName(),
                team.getTeamId(),
                team.getTeamName());
    }

    /**
     * Shared account creation: unique email, role lookup, BCrypt encoding, insert.
     * Only the hash is ever written - the raw password is never stored.
     * A null team leaves RESOLVE_USER.TEAM_ID null, which is what self-registered
     * USER accounts get.
     */
    private AppUser createAccount(String name, String email, String rawPassword, String roleName,
                                  TeamService team) {
        String trimmedEmail = email.trim();

        if (appUserRepository.existsByEmailIgnoreCase(trimmedEmail)) {
            throw new ConflictException("Email is already registered");
        }

        Role role = roleRepository.findByRoleNameIgnoreCase(roleName)
                .orElseThrow(() -> new IllegalStateException(
                        roleName + " role is missing from the ROLE table"));

        AppUser user = new AppUser();
        user.setName(name.trim());
        user.setEmail(trimmedEmail);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setTeam(team);

        return appUserRepository.save(user);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (AuthenticationException ex) {
            throw new UnauthorizedException("Invalid email or password");
        }

        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        String token = jwtService.generateToken(user);

        return new LoginResponse(token, user.getUserId(), user.getName(), user.getRole());
    }

    /**
     * Direct logout: revokes the exact access token the caller presented, and nothing
     * else. The caller's other tokens, and every other user's tokens, keep working.
     *
     * No refresh token exists in this system, so there is nothing else to invalidate -
     * once this token is on the revocation list the caller has to log in again to get a
     * new one. Not transactional: revocation touches no database row.
     */
    public LogoutResponse logout(String rawToken) {
        TokenIdentity identity = jwtService.extractIdentity(rawToken)
                .orElseThrow(() -> new UnauthorizedException("Authentication is required"));

        revocationService.revoke(identity.tokenId(), identity.expiresAt());

        return new LogoutResponse("Logged out successfully");
    }
}
