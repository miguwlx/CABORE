<?php
session_start();
include("conexion.php");
require 'vendor/autoload.php';
require 'config_correo.php';

use PHPMailer\PHPMailer\PHPMailer;
use PHPMailer\PHPMailer\Exception;

function enviarCorreoRecuperacion($destinatario, $nombre, $enlace) {
    $mail = new PHPMailer(true);
    try {
        $mail->isSMTP();
        $mail->Host       = SMTP_HOST;
        $mail->SMTPAuth   = true;
        $mail->Username   = SMTP_USER;
        $mail->Password   = SMTP_PASS;
        $mail->SMTPSecure = PHPMailer::ENCRYPTION_STARTTLS;
        $mail->Port       = SMTP_PORT;
        $mail->CharSet    = 'UTF-8';

        $mail->setFrom(SMTP_USER, SMTP_FROM_NAME);
        $mail->addAddress($destinatario, $nombre);

        $mail->isHTML(true);
        $mail->Subject = 'Recupera tu contraseña — Caboré';
        $mail->Body    = '
            <div style="font-family:Arial,sans-serif;max-width:480px;margin:0 auto">
              <h2 style="color:#b8923c">Caboré</h2>
              <p>Hola ' . htmlspecialchars($nombre) . ',</p>
              <p>Recibimos una solicitud para restablecer tu contraseña. Haz clic en el siguiente botón (válido por 1 hora):</p>
              <p style="text-align:center;margin:28px 0">
                <a href="' . $enlace . '" style="background:#b8923c;color:#fff;padding:14px 28px;border-radius:10px;text-decoration:none;font-weight:600">Restablecer contraseña</a>
              </p>
              <p style="font-size:12px;color:#888">Si no solicitaste esto, ignora este correo — tu contraseña seguirá siendo la misma.</p>
            </div>
        ';
        $mail->AltBody = "Restablece tu contraseña en: " . $enlace;

        $mail->send();
        return true;
    } catch (Exception $e) {
        error_log("Error enviando correo de recuperación: " . $mail->ErrorInfo);
        return false;
    }
}

$correo   = trim($_POST['correo'] ?? '');
$mensaje  = '';
$es_error = false;

if ($correo === '' || !filter_var($correo, FILTER_VALIDATE_EMAIL)) {
    $mensaje  = "Ingresa un correo válido.";
    $es_error = true;
} else {
    $stmt = $conn->prepare("SELECT id, nombre FROM usuarios WHERE correo = ?");
    $stmt->bind_param("s", $correo);
    $stmt->execute();
    $usuario = $stmt->get_result()->fetch_assoc();

    // Por seguridad, siempre mostramos el mismo mensaje exista o no la cuenta
    $mensaje = "Si el correo existe en nuestra base de datos, te enviamos un enlace de recuperación.";

    if ($usuario) {
        $token  = bin2hex(random_bytes(32));
        $expira = date('Y-m-d H:i:s', strtotime('+1 hour'));

        $upd = $conn->prepare("UPDATE usuarios SET reset_token = ?, reset_expira = ? WHERE id = ?");
        $upd->bind_param("ssi", $token, $expira, $usuario['id']);
        $upd->execute();

        $enlace = "http://localhost/CABORE-GIT/restablecer_password.php?token=" . $token;
        enviarCorreoRecuperacion($correo, $usuario['nombre'], $enlace);
    }
}
?>
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Caboré — Recuperación enviada</title>
<link href="https://fonts.googleapis.com/css2?family=Cormorant+Garamond:ital,wght@0,400;0,600;0,700;1,400&family=Outfit:wght@300;400;500;600;700&display=swap" rel="stylesheet">
<link rel="stylesheet" href="css/recuperacion.css">
</head>
<body>

<div class="noise"></div>
<div class="bg-glow a"></div>
<div class="bg-glow b"></div>

<div class="recover-card" style="text-align:center">
  <?php if ($es_error): ?>
    <div class="recover-icon" style="margin:0 auto 20px;background:linear-gradient(135deg,#f87171,#dc2626)">⚠️</div>
    <div class="recover-title">Algo salió mal</div>
    <p class="recover-sub"><?= htmlspecialchars($mensaje) ?></p>
    <a href="olvide_password.html" class="btn-gold" style="text-decoration:none">Intentar de nuevo</a>
  <?php else: ?>
    <div class="recover-icon" style="margin:0 auto 20px;background:linear-gradient(135deg,#34d399,#0f9d63)">📩</div>
    <div class="recover-title">Revisa tu correo</div>
    <p class="recover-sub"><?= htmlspecialchars($mensaje) ?></p>
    <a href="login.html" class="btn-gold" style="text-decoration:none">← Volver a iniciar sesión</a>
  <?php endif; ?>
</div>

</body>
</html>