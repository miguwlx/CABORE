<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<title>Registro - Caboré</title>
</head>
<body>

<h2>Registro</h2>

<form action="registro.php" method="POST">
    <input type="text" name="nombre" placeholder="Nombre" required>
    <input type="email" name="correo" placeholder="Correo" required>
    <input type="password" name="contrasena" placeholder="Contraseña" required>

    <select name="rol">
        <option value="cliente">Cliente</option>
        <option value="emprendedor">Emprendedor</option>
    </select>

    <button type="submit">Registrarse</button>
</form>

<p><a href="index.html">Ya tengo cuenta — Iniciar sesión</a></p>

</body>
</html>
