/* ==========================================================
   validaciones.js — Validación de formularios en el frontend
   Caboré. Archivo independiente, no se mezcla con otros JS.
   ========================================================== */
(function () {
    "use strict";

    const REGEX_EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

    function mostrarError(input, mensaje) {
        limpiarError(input);
        input.classList.add('input-invalido');
        input.style.borderColor = '#ef4444';
        const small = document.createElement('small');
        small.className = 'mensaje-error';
        small.style.color = '#ef4444';
        small.style.display = 'block';
        small.style.marginTop = '4px';
        small.style.fontSize = '12px';
        small.textContent = mensaje;
        input.insertAdjacentElement('afterend', small);
    }

    function limpiarError(input) {
        input.classList.remove('input-invalido');
        input.style.borderColor = '';
        const siguiente = input.nextElementSibling;
        if (siguiente && siguiente.classList.contains('mensaje-error')) {
            siguiente.remove();
        }
    }

    function validarEmail(input) {
        const valor = input.value.trim();
        if (!REGEX_EMAIL.test(valor)) {
            mostrarError(input, 'Ingresa un correo electrónico válido.');
            return false;
        }
        limpiarError(input);
        return true;
    }

    function validarPassword(input, minimo) {
        minimo = minimo || 6;
        if (input.value.length < minimo) {
            mostrarError(input, 'La contraseña debe tener al menos ' + minimo + ' caracteres.');
            return false;
        }
        limpiarError(input);
        return true;
    }

    function validarNombre(input) {
        if (input.value.trim().length < 3) {
            mostrarError(input, 'Ingresa tu nombre completo.');
            return false;
        }
        limpiarError(input);
        return true;
    }

    function validarConfirmacion(input, otroInput) {
        if (input.value !== otroInput.value) {
            mostrarError(input, 'Las contraseñas no coinciden.');
            return false;
        }
        limpiarError(input);
        return true;
    }

    /* ── Formulario de registro ── */
    const regForm = document.getElementById('regForm');
    if (regForm) {
        const nombre   = regForm.querySelector('input[name="nombre"]');
        const correo   = regForm.querySelector('input[name="correo"]');
        const password = regForm.querySelector('input[name="contrasena"]');

        nombre.addEventListener('blur', () => validarNombre(nombre));
        correo.addEventListener('blur', () => validarEmail(correo));
        password.addEventListener('blur', () => validarPassword(password, 6));

        regForm.addEventListener('submit', (ev) => {
            const ok = validarNombre(nombre) & validarEmail(correo) & validarPassword(password, 6);
            if (!ok) ev.preventDefault();
        });
    }

    /* ── Formularios de login (cliente y emprendedor) ── */
    document.querySelectorAll('.form-panel form').forEach(form => {
        const correo   = form.querySelector('input[type="email"]');
        const password = form.querySelector('input[type="password"]');
        if (!correo || !password) return;

        correo.addEventListener('blur', () => validarEmail(correo));

        form.addEventListener('submit', (ev) => {
            const okCorreo = validarEmail(correo);
            const okPass   = password.value.length > 0;
            if (!okPass) mostrarError(password, 'Ingresa tu contraseña.');
            else limpiarError(password);
            if (!okCorreo || !okPass) ev.preventDefault();
        });
    });

    /* ── Formulario "olvidé mi contraseña" ── */
    const olvideForm = document.getElementById('olvideForm');
    if (olvideForm) {
        const correo = olvideForm.querySelector('input[name="correo"]');
        olvideForm.addEventListener('submit', (ev) => {
            if (!validarEmail(correo)) ev.preventDefault();
        });
    }

    /* ── Formulario para restablecer contraseña ── */
    const resetForm = document.getElementById('resetForm');
    if (resetForm) {
        const nueva     = document.getElementById('nueva_contrasena');
        const confirmar = document.getElementById('confirmar_contrasena');
        resetForm.addEventListener('submit', (ev) => {
            const okPass = validarPassword(nueva, 8);
            const okConf = validarConfirmacion(confirmar, nueva);
            if (!okPass || !okConf) ev.preventDefault();
        });
    }
})();