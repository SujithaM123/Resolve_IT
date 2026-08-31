// Injected as the first entries of the SwaggerUIBundle({...}) config object by
// SwaggerAutoTokenTransformer. Browser-side only - it changes nothing on the server.
//
// responseInterceptor keeps the JWT that POST /api/auth/login hands back, and drops it
// again when POST /api/auth/logout revokes it.
// requestInterceptor replays it on every later call, so the reviewer never copies a
// token by hand and the page needs no Authorize button.

    requestInterceptor: function (req) {
      try {
        var url = String(req.url || '');
        var isPublicAuth = url.indexOf('/api/auth/login') !== -1
                        || url.indexOf('/api/auth/register') !== -1;
        var token = window.sessionStorage.getItem('resolveit.jwt');
        if (token && !isPublicAuth) {
          req.headers = req.headers || {};
          req.headers['Authorization'] = 'Bearer ' + token;
        }
      } catch (e) { /* never break the request */ }
      return req;
    },
    responseInterceptor: function (res) {
      try {
        var url = String(res.url || '');
        if (url.indexOf('/api/auth/login') !== -1 && res.status === 200) {
          var body = res.body;
          if (!body && res.text) { body = JSON.parse(res.text); }
          if (typeof body === 'string') { body = JSON.parse(body); }
          if (body && body.token) {
            window.sessionStorage.setItem('resolveit.jwt', body.token);
            window.sessionStorage.setItem('resolveit.who',
              (body.name || '') + ' - ' + (body.role || ''));
            if (window.resolveItBanner) { window.resolveItBanner(); }
          }
        }
        // Logout revokes this token on the server, so holding on to it would only
        // produce 401s on every later call. Forget it and show the page as signed out.
        if (url.indexOf('/api/auth/logout') !== -1 && res.status === 200) {
          window.sessionStorage.removeItem('resolveit.jwt');
          window.sessionStorage.removeItem('resolveit.who');
          if (window.resolveItBanner) { window.resolveItBanner(); }
        }
      } catch (e) { /* never break the response */ }
      return res;
    },
    onComplete: function () {
      if (window.resolveItBanner) { window.resolveItBanner(); }
      if (window.resolveItNav) { window.resolveItNav.init(); }
    },
