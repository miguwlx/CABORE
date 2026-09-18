/* ==========================================================
   google-auth.js — Registro / inicio de sesión con Google
   Caboré. Archivo independiente.
   ========================================================== */
function handleGoogleCredential(response) {
    const rolInput = document.querySelector('input[name="rol"]:checked');
    const rol = rolInput ? rolInput.value : 'cliente';

    fetch('google_auth.php', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'credential=' + encodeURIComponent(response.credential) + '&rol=' + encodeURIComponent(rol)
    })
    .then(r => r.json())
    .then(data => {
        if (data.ok) {
            window.location.href = data.redirect;
        } else {
            alert(data.error || 'No se pudo continuar con Google.');
        }
    })
    .catch(() => alert('Error de conexión con Google.'));
}