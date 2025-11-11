(function() {
  const params = new URLSearchParams(window.location.search);
  if (params.has('code')) {
    try {
      const encoded = params.get('code');

      const bytes = Uint8Array.from(atob(encoded), c => c.charCodeAt(0));
      const decoder = new TextDecoder('utf-8');
      const decoded = decoder.decode(bytes);

      const codeDiv = document.getElementById('code');
      codeDiv.textContent = decoded;
    } catch (e) {
      console.error('Failed to decode code parameter:', e);
    }
  }
})();
