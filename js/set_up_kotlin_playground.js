document.addEventListener('DOMContentLoaded', function() {
    const options = {
        server: 'https://api.decoroutinator.reformator.dev/decoroutinator-kotlin-compiler',
        version: '1.9.23',
        getInstance: function(instance) {
            window.kotlinPlaygroundApi = instance
        }
    };
    KotlinPlayground('.kotlin-code', options);
});
