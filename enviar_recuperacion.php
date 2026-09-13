<?php
session_start();
include("conexion.php");

$correo  = trim($_POST['correo'] ?? '');
$mensaje = '';

if ($correo === '' || !filter_var($correo, FILTER_VALIDATE_EMAIL)) {
    $mensaje = "Ingresa un correo válido.";
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

        $asunto  = "Caboré — Recupera tu contraseña";
        $cuerpo  = "Hola " . $usuario['nombre'] . ",\n\nHaz clic en el siguiente enlace para restablecer tu contraseña (válido por 1 hora):\n" . $enlace . "\n\nSi no solicitaste esto, ignora este correo.";
        $headers = "From: no-reply@cabore.co";

        @mail($correo, $asunto, $cuerpo, $headers);

        // ⚠️ SOLO PARA DESARROLLO LOCAL: mail() normalmente no funciona en XAMPP
        // sin configurar un SMTP. Mostramos el enlace en pantalla para poder probar.
        // Bórralo cuando pases esto a un hosting real con correo configurado.
        $_SESSION['debug_link'] = $enlace;
    }
}
?>
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<title>Caboré — Recuperación enviada</title>
<link rel="stylesheet" href="css/login.css">
</head>
<body>
<div class="page" style="display:flex;align-items:center;justify-content:center;min-height:100vh">
  <div class="panel-area" style="max-width:420px;text-align:center">
    <div class="panel-title">📩 Revisa tu correo</div>
    <p class="panel-sub"><?= htmlspecialchars($mensaje) ?></p>

    <?php if (!empty($_SESSION['debug_link'])): ?>
      <p style="font-size:12px;color:#999;margin-top:20px">
        Modo desarrollo — como el correo local no está configurado, aquí está el enlace:<br>
        <a href="<?= htmlspecialchars($_SESSION['debug_link']) ?>"><?= htmlspecialchars($_SESSION['debug_link']) ?></a>
      </p>
      <?php unset($_SESSION['debug_link']); ?>
    <?php endif; ?>

    <a href="login.html" class="btn-submit btn--blue" style="display:inline-block;margin-top:20px;text-decoration:none">← Volver a iniciar sesión</a>
  </div>
</div>
</body>
</html>