document.getElementById('copyLink').addEventListener('click', async () => {
    const code = window.kotlinPlaygroundApi.getCode();

    const bytes = new TextEncoder().encode(code);
    let binary = '';
    bytes.forEach(b => binary += String.fromCharCode(b));
    const encoded = btoa(binary);

    const url = new URL(window.location.href);
    url.searchParams.set('code', encoded);
    const link = url.toString();

    const copyLinkBtn = document.getElementById('copyLink');

    try {
        await navigator.clipboard.writeText(link);
        copyLinkBtn.textContent = "Copied!";
        setTimeout(() => (copyLinkBtn.textContent = "Copy link"), 1500);
    } catch (err) {
        console.error("Failed to copy:", err);
        alert("Could not copy to clipboard. " + link);
    }
});
