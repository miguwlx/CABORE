/* ==========================================================
   google-auth.js — Registro / inicio de sesión con Google
   Caboré. Archivo independiente (migrado a Spring Boot).
   ========================================================== */
function mostrarErrorGoogle(msg) {
    const panelEmp = document.getElementById('panel-emprendedor');
    const esEmp = panelEmp && panelEmp.classList.contains('active');
    const boxLogin = document.getElementById(esEmp ? 'err-emprendedor' : 'err-cliente');
    if (boxLogin) {
        boxLogin.innerHTML = '⚠️&nbsp; ' + msg;
        boxLogin.classList.add('visible');
        return;
    }
    const boxReg = document.getElementById('err-registro');
    if (boxReg) {
        boxReg.textContent = msg;
        boxReg.style.display = 'block';
        return;
    }
    alert(msg);
}

function handleGoogleCredential(response) {
    if (!response || !response.credential) {
        mostrarErrorGoogle('No se recibió la credencial de Google.');
        return;
    }

    const rolInput = document.querySelector('input[name="rol"]:checked');
    const panelEmp = document.getElementById('panel-emprendedor');
    let rol = 'cliente';
    if (rolInput) {
        rol = rolInput.value;
    } else if (panelEmp && panelEmp.classList.contains('active')) {
        rol = 'emprendedor';
    }

    const csrfToken  = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN';

    const headers = { 'Content-Type': 'application/x-www-form-urlencoded' };
    if (csrfToken) {
        headers[csrfHeader] = csrfToken;
    }

    fetch('/auth/google', {
        method: 'POST',
        headers: headers,
        body: 'credential=' + encodeURIComponent(response.credential) + '&rol=' + encodeURIComponent(rol)
    })
    .then(r => r.json())
    .then(data => {
        if (data.ok) {
            window.location.href = data.redirect;
        } else {
            mostrarErrorGoogle(data.error || 'No se pudo continuar con Google.');
        }
    })
    .catch(() => mostrarErrorGoogle('Error de conexión al verificar con Google.'));
}
