// ResolveIT additions to the Swagger page. Prepended to swagger-initializer.js by
// SwaggerAutoTokenTransformer.
//
//   window.resolveItBanner - session strip showing who is signed in.
//   window.resolveItNav    - toolbar: search box + module filter + access filter.
//
// ALL OF IT IS DISPLAY ONLY. The filters hide and show rows Swagger has already drawn.
// They grant nothing and send nothing. Choosing "SUPER_ADMIN" does not make the viewer a
// super admin - an endpoint their role cannot reach still answers 403 from Spring
// Security, exactly as it does from curl.

// ---------------------------------------------------------------- session strip
window.resolveItBanner = function () {
  try {
    var el = document.getElementById('resolveit-session');
    if (!el) {
      el = document.createElement('div');
      el.id = 'resolveit-session';
      el.style.cssText = 'font:14px/1.5 -apple-system,BlinkMacSystemFont,Segoe UI,Roboto,sans-serif;'
        + 'padding:9px 20px;display:flex;gap:12px;align-items:center;flex-wrap:wrap';
      document.body.insertBefore(el, document.body.firstChild);
    }
    var who = window.sessionStorage.getItem('resolveit.who');
    var tok = window.sessionStorage.getItem('resolveit.jwt');
    if (tok) {
      el.style.background = '#dafbe1';
      el.style.color = '#1a7f37';
      el.innerHTML = '<b>Signed in:</b> ' + who
        + '<span style="opacity:.8">Swagger is attaching this JWT to every protected request.</span>'
        + '<a href="#" id="resolveit-signout" style="margin-left:auto;color:#cf222e;font-weight:600">Sign out</a>';
      var out = document.getElementById('resolveit-signout');
      if (out) {
        out.onclick = function (e) {
          e.preventDefault();
          window.sessionStorage.removeItem('resolveit.jwt');
          window.sessionStorage.removeItem('resolveit.who');
          window.resolveItBanner();
        };
      }
    } else {
      el.style.background = '#fff8c5';
      el.style.color = '#9a6700';
      el.innerHTML = '<b>Not signed in.</b>'
        + '<span style="opacity:.8">Protected endpoints return 401. '
        + 'Call POST /api/auth/login to start a session.</span>';
    }
  } catch (e) { /* the page must still work without the strip */ }
};
document.addEventListener('DOMContentLoaded', function () { window.resolveItBanner(); });

// ---------------------------------------------------------------- toolbar + filters
window.resolveItNav = (function () {
  'use strict';

  // The ownership-checked endpoints clear the URL rule for any signed-in caller, but
  // IncidentAccessService then recognises only the reporter and the assigned engineer.
  // SUPER_ADMIN is refused all three, so it is deliberately not listed for them.
  var OWNER_ROLES = ['Authenticated', 'USER', 'SUPPORT'];

  // [ method, path, module, accessLabel, rolesItAppearsUnder ]
  // Mirrors SecurityConfig. Data, not logic - a new endpoint is one more line.
  var REST = [
    ['POST',  '/api/auth/login',                            'Authentication', 'Public',        ['Public']],
    ['POST',  '/api/auth/register',                         'Authentication', 'Public',        ['Public']],

    ['GET',   '/api/user/dashboard',                        'User Dashboard', 'USER',          ['USER']],
    ['POST',  '/api/incidents/classify',                    'Incidents',      'USER',          ['USER']],
    ['POST',  '/api/incidents',                             'Incidents',      'USER',          ['USER']],

    ['GET',   '/api/support/dashboard',                     'Support',        'SUPPORT',       ['SUPPORT']],
    ['PATCH', '/api/support/incidents/{incidentId}',        'Support',        'SUPPORT',       ['SUPPORT']],
    ['POST',  '/api/support/incidents/{incidentId}/ops-ai', 'Support',        'SUPPORT',       ['SUPPORT']],

    ['GET',   '/api/teams',                                 'Teams',          'SUPER_ADMIN',   ['SUPER_ADMIN']],
    ['POST',  '/api/support-users',                         'Support User Management', 'SUPER_ADMIN', ['SUPER_ADMIN']],

    ['GET',   '/api/incidents/{incidentId}',                'Incidents',      'Authenticated', OWNER_ROLES],
    ['POST',  '/api/incidents/{incidentId}/messages',       'Incident Conversation', 'Authenticated', OWNER_ROLES],
    ['PATCH', '/api/incidents/{incidentId}/messages/read',  'Incident Conversation', 'Authenticated', OWNER_ROLES]
  ];

  // The seven modules, in the order they are presented in the dropdown.
  var MODULES = [
    'Authentication',
    'Incidents',
    'Incident Conversation',
    'User Dashboard',
    'Support',
    'Teams',
    'Support User Management'
  ];

  var ACCESS_LEVELS = ['Public', 'USER', 'SUPPORT', 'SUPER_ADMIN', 'Authenticated'];

  var ACCESS_COLOUR = {
    'Public':        ['#dafbe1', '#1a7f37'],
    'USER':          ['#ddf4ff', '#0969da'],
    'SUPPORT':       ['#d3f9f0', '#0f766e'],
    'SUPER_ADMIN':   ['#fff8c5', '#9a6700'],
    'Authenticated': ['#eaeef2', '#57606a']
  };

  var lookup = {};
  REST.forEach(function (r) { lookup[r[0] + ' ' + r[1]] = { module: r[2], access: r[3], roles: r[4] }; });

  var state = { q: '', module: 'all', access: 'all' };
  var applying = false;

  function chip(access) {
    var c = ACCESS_COLOUR[access] || ACCESS_COLOUR['Authenticated'];
    return '<span style="background:' + c[0] + ';color:' + c[1] + ';border:1px solid ' + c[1]
      + '33;border-radius:20px;padding:2px 9px;font:600 11px/1.7 ui-monospace,SFMono-Regular,monospace;'
      + 'white-space:nowrap">' + access + '</span>';
  }

  // Styling follows the reference toolbar: 38px controls, GitHub-style borders.
  var FIELD = 'height:38px;border:1px solid #d0d7de;border-radius:6px;font-size:14px;'
    + 'line-height:20px;outline:none;background:#fff;box-shadow:0 1px 2px rgba(0,0,0,.05);'
    + 'transition:border-color .2s ease,box-shadow .2s ease;box-sizing:border-box;'
    + 'font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,sans-serif';

  var CARET = "background-image:url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' "
    + "width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%23666' d='M6 9L1 4h10z'/%3E%3C/svg%3E\");"
    + 'background-repeat:no-repeat;background-position:right 10px center;';

  var SELECT_CSS = FIELD + 'min-width:200px;cursor:pointer;padding:8px 35px 8px 12px;'
    + 'appearance:none;-webkit-appearance:none;color:#24292f;' + CARET;

  // Swagger UI's own stylesheet caps text inputs at 200px with !important, which clips
  // the placeholder now that it is the only label on the box. Override it, and stop flex
  // from shrinking it again when the toolbar wraps.
  var INPUT_CSS = FIELD + 'width:330px !important;min-width:330px;flex:0 0 330px;'
    + 'padding:9px 14px;color:#24292f';

  function focusRing(el) {
    el.addEventListener('focus', function () {
      this.style.borderColor = '#0969da';
      this.style.boxShadow = '0 0 0 3px rgba(9,105,218,.1)';
    });
    el.addEventListener('blur', function () {
      this.style.borderColor = '#d0d7de';
      this.style.boxShadow = '0 1px 2px rgba(0,0,0,.05)';
    });
  }

  // ------------------------------------------------------------ toolbar
  function buildToolbar() {
    if (document.getElementById('resolveit-toolbar')) { return true; }

    // Sit inline in the Servers bar when it exists, so the controls read as part of the
    // page header rather than as a separate panel.
    var host = document.querySelector('.scheme-container .schemes');
    var fallback = document.querySelector('.opblock-tag-section');
    if (!host && !fallback) { return false; }

    var bar = document.createElement('div');
    bar.id = 'resolveit-toolbar';

    var moduleOptions = '<option value="all">Show All Modules</option>'
      + MODULES.map(function (m) { return '<option value="' + m + '">' + m + '</option>'; }).join('');

    var accessOptions = '<option value="all">All Access Levels</option>'
      + ACCESS_LEVELS.map(function (a) { return '<option value="' + a + '">' + a + '</option>'; }).join('');

    var note = 'Display filter only. It changes what this page lists, never what the server '
             + 'allows - calling an endpoint your role lacks still returns 403.';

    bar.innerHTML =
        '<input id="resolveit-search" type="text" autocomplete="off" '
      +   'placeholder="Search endpoints, tags, or modules..." style="' + INPUT_CSS + '">'
      + '<select id="resolveit-module" title="' + note + '" style="' + SELECT_CSS + '">'
      +   moduleOptions + '</select>'
      + '<select id="resolveit-access" title="' + note + '" style="' + SELECT_CSS + '">'
      +   accessOptions + '</select>'
      + '<span id="resolveit-count" style="color:#57606a;font-size:13px;white-space:nowrap;'
      +   'font-variant-numeric:tabular-nums"></span>';

    if (host) {
      bar.style.cssText = 'display:inline-flex;gap:10px;align-items:center;margin-left:auto;'
        + 'flex-wrap:wrap;justify-content:flex-end';
      host.style.display = 'flex';
      host.style.alignItems = 'center';
      host.style.flexWrap = 'wrap';
      host.appendChild(bar);
    } else {
      bar.style.cssText = 'display:flex;gap:10px;align-items:center;flex-wrap:wrap;'
        + 'justify-content:flex-end;margin:0 0 18px;padding:12px 20px';
      fallback.parentNode.insertBefore(bar, fallback);
    }

    var search = document.getElementById('resolveit-search');
    focusRing(search);
    search.title = note;
    search.addEventListener('input', function (e) {
      state.q = e.target.value.toLowerCase().trim();
      apply();
    });

    var mod = document.getElementById('resolveit-module');
    var acc = document.getElementById('resolveit-access');
    focusRing(mod);
    focusRing(acc);
    mod.addEventListener('change', function (e) { state.module = e.target.value; apply(); });
    acc.addEventListener('change', function (e) { state.access = e.target.value; apply(); });

    return true;
  }

  // ------------------------------------------------------------ tagging
  function tagOperations() {
    var blocks = document.querySelectorAll('.opblock');
    for (var i = 0; i < blocks.length; i++) {
      var b = blocks[i];
      var mEl = b.querySelector('.opblock-summary-method');
      var pEl = b.querySelector('.opblock-summary-path');
      if (!mEl || !pEl) { continue; }

      var method = (mEl.textContent || '').trim().toUpperCase();
      var path = (pEl.getAttribute('data-path') || pEl.textContent || '').trim();
      var m = lookup[method + ' ' + path] || null;

      b.setAttribute('data-ri-method', method);
      b.setAttribute('data-ri-path', path);
      b.setAttribute('data-ri-module', m ? m.module : '');
      b.setAttribute('data-ri-access', m ? m.access : '');
      b.setAttribute('data-ri-roles', m ? m.roles.join('|') : '');

      var descEl = b.querySelector('.opblock-summary-description');
      var tagEl = b.closest('.opblock-tag-section');
      var tagName = tagEl ? ((tagEl.querySelector('.opblock-tag') || {}).textContent || '') : '';
      b.setAttribute('data-ri-text',
        (((descEl ? descEl.textContent : '') || '') + ' ' + tagName).toLowerCase());

      if (m && !b.querySelector('.resolveit-access-chip')) {
        var holder = b.querySelector('.opblock-summary');
        if (holder) {
          var span = document.createElement('span');
          span.className = 'resolveit-access-chip';
          span.style.cssText = 'margin-left:8px;align-self:center;display:inline-flex';
          span.innerHTML = chip(m.access);
          holder.appendChild(span);
        }
      }
    }
  }

  // ------------------------------------------------------------ filtering
  function matches(method, path, module, access, roles, text) {
    if (state.module !== 'all' && module !== state.module) { return false; }
    if (state.access !== 'all' && roles.indexOf(state.access) === -1) { return false; }
    if (state.q) {
      var hay = (method + ' ' + path + ' ' + module + ' ' + access + ' ' + text).toLowerCase();
      if (hay.indexOf(state.q) === -1) { return false; }
    }
    return true;
  }

  function apply() {
    if (applying) { return; }
    applying = true;
    try {
      var shown = 0, total = 0, i;

      var blocks = document.querySelectorAll('.opblock');
      for (i = 0; i < blocks.length; i++) {
        var b = blocks[i];
        total++;
        var ok = matches(
          b.getAttribute('data-ri-method') || '',
          b.getAttribute('data-ri-path') || '',
          b.getAttribute('data-ri-module') || '',
          b.getAttribute('data-ri-access') || '',
          (b.getAttribute('data-ri-roles') || '').split('|'),
          b.getAttribute('data-ri-text') || '');
        b.style.display = ok ? '' : 'none';
        if (ok) { shown++; }
      }

      // Hide a controller heading once all of its operations are filtered out.
      var sections = document.querySelectorAll('.opblock-tag-section');
      for (i = 0; i < sections.length; i++) {
        var kids = sections[i].querySelectorAll('.opblock');
        var any = false;
        for (var k = 0; k < kids.length; k++) {
          if (kids[k].style.display !== 'none') { any = true; break; }
        }
        sections[i].style.display = any ? '' : 'none';
      }

      var count = document.getElementById('resolveit-count');
      if (count) {
        count.textContent = (shown === total) ? ('showing all ' + total)
                                              : ('showing ' + shown + ' of ' + total);
      }

      var empty = document.getElementById('resolveit-empty');
      if (!empty) {
        var firstSection = document.querySelector('.opblock-tag-section');
        if (firstSection) {
          empty = document.createElement('div');
          empty.id = 'resolveit-empty';
          empty.style.cssText = 'display:none;padding:32px;text-align:center;color:#57606a;'
            + 'border:1px dashed #d0d7de;border-radius:8px;margin:0 20px 20px;'
            + 'font:14px -apple-system,BlinkMacSystemFont,Segoe UI,Roboto,sans-serif';
          empty.textContent = 'No endpoints match this search and filter combination.';
          firstSection.parentNode.insertBefore(empty, firstSection);
        }
      }
      if (empty) { empty.style.display = shown === 0 ? 'block' : 'none'; }
    } finally {
      applying = false;
    }
  }

  // ------------------------------------------------------------ wiring
  var observer = null;

  function init() {
    var firstSection = document.querySelector('.opblock-tag-section');
    if (!firstSection) { return; }

    buildToolbar();
    tagOperations();
    apply();

    if (!observer) {
      var pending = null;
      observer = new MutationObserver(function () {
        if (applying) { return; }
        clearTimeout(pending);
        pending = setTimeout(function () {
          // Swagger may re-render and drop the toolbar; rebuild it if it went away.
          buildToolbar();
          tagOperations();
          apply();
        }, 120);
      });
      observer.observe(firstSection.parentNode, { childList: true, subtree: true });
    }
  }

  return { init: init, apply: apply };
})();
