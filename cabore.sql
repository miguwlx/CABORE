<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<title>Caboré</title>

<style>
* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
    font-family: 'Segoe UI', sans-serif;
}

body {
    display: flex;
    height: 100vh;
    overflow: hidden;
}

/* MITADES */
.seccion {
    width: 50%;
    display: flex;
    justify-content: center;
    align-items: center;
    transition: 0.5s;
    flex-direction: column;
}

/* EFECTO HOVER GENERAL */
.seccion:hover {
    width: 55%;
}

/* CLIENTE */
.cliente {
    background: linear-gradient(135deg, #e6f0ff, #cce0ff);
}

/* EMPRENDEDOR */
.emprendedor {
    background: linear-gradient(135deg, #1e3a5f, #0f1f33);
    color: white;
}

/* CARD */
.card {
    background: white;
    padding: 40px;
    border-radius: 15px;
    box-shadow: 0px 10px 30px rgba(0,0,0,0.2);
    text-align: center;
    width: 300px;
    transform: translateY(50px);
    opacity: 0;
    animation: aparecer 1s forwards;
}

/* EFECTO GLASS */
.emprendedor .card {
    background: rgba(255,255,255,0.1);
    backdrop-filter: blur(10px);
    color: white;
}

/* ANIMACIÓN ENTRADA */
@keyframes aparecer {
    to {
        transform: translateY(0);
        opacity: 1;
    }
}

/* TITULOS */
h2 {
    margin-bottom: 20px;
}

/* INPUTS */
input[type="email"],
input[type="password"] {
    width: 100%;
    padding: 10px;
    margin: 10px 0;
    border-radius: 8px;
    border: none;
    outline: none;
    transition: 0.3s;
    background: #f1f1f1;
    color: #333;
}

input:focus {
    transform: scale(1.05);
}

/* BOTONES */
button {
    width: 100%;
    padding: 10px;
    margin-top: 10px;
    border-radius: 8px;
    border: none;
    cursor: pointer;
    font-weight: bold;
    transition: 0.3s;
}

/* COLORES */
.cliente button.btn-main {
    background: #2b7cff;
    color: white;
}

.emprendedor button.btn-main {
    background: #00c896;
    color: black;
}

/* BOTÓN REGISTRO */
.btn-registro {
    background: transparent;
    border: 2px solid currentColor;
    opacity: 0.7;
    margin-top: 8px;
    font-size: 13px;
}

/* HOVER BOTÓN */
button:hover {
    transform: scale(1.05);
    opacity: 0.9;
}

/* LOGO */
.logo {
    position: absolute;
    top: 20px;
    left: 20px;
    font-size: 24px;
    font-weight: bold;
    animation: fadeIn 2s;
    z-index: 10;
}

/* ANIMACIÓN LOGO */
@keyframes fadeIn {
    from { opacity: 0; }
    to { opacity: 1; }
}

/* TEXTO EXTRA */
.subtexto {
    font-size: 12px;
    margin-top: 10px;
    opacity: 0.7;
}

/* EFECTO FONDO MOVIMIENTO */
body::before {
    content: "";
    position: absolute;
    width: 200%;
    height: 200%;
    background: radial-gradient(circle, rgba(255,255,255,0.1), transparent);
    animation: mover 10s infinite linear;
    pointer-events: none;
}

@keyframes mover {
    from { transform: translate(0,0); }
    to { transform: translate(-50%, -50%); }
}
</style>

</head>

<body>

<div class="logo">Caboré</div>


<div class="seccion cliente">
    <div class="card">
        <h2>Cliente 🛒</h2>
        <form action="login.php" method="POST">
            <input type="hidden" name="tipo" value="cliente">
            <input type="email" name="correo" placeholder="Correo" required>
            <input type="password" name="contrasena" placeholder="Contraseña" required>
            <button type="submit" class="btn-main">Ingresar</button>
        </form>
        <button class="btn-registro" onclick="window.location.href='home.php'">Crear cuenta nueva</button>
        <div class="subtexto">Explora y compra productos</div>
    </div>
</div>


<div class="seccion emprendedor">
    <div class="card">
        <h2>Emprendedor 🚀</h2>
        <form action="login.php" method="POST">
            <input type="hidden" name="tipo" value="emprendedor">
            <input type="email" name="correo" placeholder="Correo" required>
            <input type="password" name="contrasena" placeholder="Contraseña" required>
            <button type="submit" class="btn-main">Ingresar</button>
        </form>
        <button class="btn-registro" onclick="window.location.href='home.php'">Crear cuenta nueva</button>
        <div class="subtexto">Gestiona tu tienda y productos</div>
    </div>
</div>

</body>
</html>
