const serializeHeaders = (h) => {
  if (!h) return "{}";
  if (h instanceof Headers) {
    const obj = {};
    h.forEach((v, k) => {
      obj[k] = v;
    });
    return JSON.stringify(obj);
  }
  return JSON.stringify(h);
};

const XHR = window.XMLHttpRequest;

function XHRProxy() {
  const xhr = new XHR();
  const state = { method: "", url: "", body: null, headers: {} };

  return new Proxy(xhr, {
    get(target, prop) {
      if (prop === "setRequestHeader") {
        return function (header, value) {
          state.headers[header] = value;
          return target.setRequestHeader(header, value);
        };
      }

      if (prop === "open") {
        return function (method, url) {
          state.method = method;
          state.url = url;
          return target.open(method, url);
        };
      }

      if (prop === "send") {
        return function (body) {
          state.body = body;
          fetch(state.url, {
            method: state.method,
            headers: state.headers,
            body: state.body,
          })
            .then((res) => res.text())
            .then((data) => {
              target.status = 200;
              target.readyState = 4;
              target.responseText = data;
              if (target.onload) target.onload();
              if (target.onreadystatechange) target.onreadystatechange();
            })
            .catch((err) => {
              target.readyState = 4;
              if (target.onerror) target.onerror(err);
            });
        };
      }

      const val = target[prop];
      return typeof val === "function" ? val.bind(target) : val;
    },
    set(target, prop, value) {
      target[prop] = value;
      return true;
    },
  });
}

XHRProxy.prototype = XHR.prototype;
window.XMLHttpRequest = XHRProxy;

const interval = window.setInterval;
window.setInterval = function (callback, delay, ...args) {
  return interval(callback, delay * 0.01, ...args);
};

Object.defineProperty(window, "setInterval", {
  value: window.setInterval,
  writable: false,
  configurable: false,
});

const timeout = window.setTimeout;
window.setTimeout = function (callback, delay, ...args) {
  return timeout(callback, delay * 0.01, ...args);
};

Object.defineProperty(window, "setTimeout", {
  value: window.setTimeout,
  writable: false,
  configurable: false,
});
