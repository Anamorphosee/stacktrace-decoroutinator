const originalFetch = window.fetch;
window.fetch = function(resource, init) {
    let url = resource instanceof Request ? resource.url : resource;
    const u = new URL(url, location.origin);
    u.searchParams.set("recoveryType", document.getElementById("recoveryType").value);
    return originalFetch.call(this, u.toString(), init);
};

const originalOpen = XMLHttpRequest.prototype.open;
XMLHttpRequest.prototype.open = function(method, url, ...rest) {
    const u = new URL(url, location.origin);
    u.searchParams.set("recoveryType", document.getElementById("recoveryType").value);
    return originalOpen.call(this, method, u.toString(), ...rest);
};
