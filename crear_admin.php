<?php
/**
 * crear_admin.php
 * ─────────────────────────────────────────────────────────────────────
 * Script de UN SOLO USO para crear la primera cuenta de administrador.
 * registro.php no permite crear cuentas con rol "administrador" a propósito
 * (nadie debería poder auto-registrarse como admin desde el formulario
 * público), así que este archivo aparte lo hace de forma controlada.
 *
 * ⚠️  IMPORTANTE:
 *   1. Cambia CLAVE_INSTALACION por una clave propia antes de usarlo.
 *   2. Después de crear tu cuenta de admin, BORRA este archivo del servidor.
 * ─────────────────────────────────────────────────────────────────────
 */
session_start();
include("conexion.php");

// 👉 Cambia este valor por una clave propia (solo tú debes conocerla).
define('CLAVE_INSTALACION', '1012371735Dr@');

$mensaje = null;
$exito   = false;

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $clave      = $_POST['clave_instalacion'] ?? '';
    $nombre     = trim($_POST['nombre'] ?? '');
    $correo     = trim($_POST['correo'] ?? '');
    $contrasena = $_POST['contrasena'] ?? '';

    if ($clave !== CLAVE_INSTALACION) {
        $mensaje = 'Clave de instalación incorrecta.';
    } elseif (!$nombre || !$correo || strlen($contrasena) < 8) {
        $mensaje = 'Completa nombre y correo, y usa una contraseña de al menos 8 caracteres.';
    } else {
        $check = $conn->prepare("SELECT id FROM usuarios WHERE correo = ?");
        $check->bind_param("s", $correo);
        $check->execute();
        $check->store_result();

        if ($check->num_rows > 0) {
            $mensaje = 'Ese correo ya está registrado.';
        } else {
            $hash = password_hash($contrasena, PASSWORD_DEFAULT);
            $rol  = 'administrador';
            $stmt = $conn->prepare("INSERT INTO usuarios (nombre, correo, contrasena, rol) VALUES (?, ?, ?, ?)");
            $stmt->bind_param("ssss", $nombre, $correo, $hash, $rol);

            if ($stmt->execute()) {
                $exito   = true;
                $mensaje = "✅ Cuenta de administrador creada correctamente. Ya puedes iniciar sesión en login.html con este correo. Ahora BORRA este archivo (crear_admin.php) del servidor.";
            } else {
                $mensaje = 'No se pudo crear la cuenta. Intenta de nuevo.';
            }
        }
    }
}
?>
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Crear administrador — Caboré</title>
<link href="https://fonts.googleapis.com/css2?family=Playfair+Display:wght@700;900&family=DM+Sans:wght@300;400;500;600&display=swap" rel="stylesheet">
<style>
:root { --gold:#c9a84c; --dark:#0d0d14; --light:#f5f0e8; --border:rgba(201,168,76,0.25); }
*,*::before,*::after{margin:0;padding:0;box-sizing:border-box;}
body{font-family:'DM Sans',sans-serif;background:var(--dark);color:var(--light);min-height:100vh;display:flex;align-items:center;justify-content:center;}
.card{background:rgba(255,255,255,0.05);backdrop-filter:blur(20px);border:1px solid var(--border);border-radius:24px;padding:44px;width:380px;}
h2{font-family:'Playfair Display',serif;font-size:24px;margin-bottom:6px;}
p.sub{color:rgba(245,240,232,0.5);font-size:13px;margin-bottom:22px;}
label{display:block;font-size:12px;font-weight:600;color:rgba(245,240,232,0.6);letter-spacing:0.4px;text-transform:uppercase;margin-bottom:6px;margin-top:16px;}
input{width:100%;padding:11px 14px;border-radius:10px;border:1px solid var(--border);background:rgba(255,255,255,0.04);color:var(--light);font-family:'DM Sans',sans-serif;font-size:14px;outline:none;}
input:focus{border-color:var(--gold);}
button{width:100%;margin-top:22px;padding:12px;border:none;border-radius:10px;background:linear-gradient(135deg,var(--gold),#a8832a);color:#0d0d14;font-weight:700;font-size:14px;cursor:pointer;}
.msg{margin-top:16px;padding:12px 14px;border-radius:10px;font-size:13px;}
.msg-ok{background:rgba(15,157,99,0.12);border:1px solid rgba(15,157,99,0.3);color:#3ddc97;}
.msg-err{background:rgba(220,38,38,0.12);border:1px solid rgba(220,38,38,0.3);color:#fca5a5;}
</style>
</head>
<body>
<div class="card">
    <h2>Crear administrador</h2>
    <p class="sub">Uso único · Caboré</p>

    <?php if ($mensaje): ?>
        <div class="msg <?= $exito ? 'msg-ok' : 'msg-err' ?>"><?= htmlspecialchars($mensaje) ?></div>
    <?php endif; ?>

    <?php if (!$exito): ?>
    <form method="POST">
        <label>Clave de instalación</label>
        <input type="password" name="clave_instalacion" required>

        <label>Nombre</label>
        <input type="text" name="nombre" required>

        <label>Correo</label>
        <input type="email" name="correo" required>

        <label>Contraseña (mín. 8 caracteres)</label>
        <input type="password" name="contrasena" required minlength="8">

        <button type="submit">Crear cuenta de administrador</button>
    </form>
    <?php endif; ?>
</div>
</body>
</html>