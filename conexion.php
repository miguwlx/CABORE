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
    height: 100vh;
    display: flex;
    overflow: hidden;
}

/* MITADES */
.seccion {
    width: 50%;
    display: flex;
    justify-content: center;
    align-items: center;
}

/* IZQUIERDA */
.izquierda {
    background: linear-gradient(135deg, #e6f0ff, #cce0ff);
    flex-direction: column;
    text-align: center;
    animation: aparecerIzq 1s ease;
}

/* DERECHA */
.derecha {
    background: linear-gradient(135deg, #1e3a5f, #0f1f33);
    color: white;
    animation: aparecerDer 1s ease;
}

/* ANIMACIONES */
@keyframes aparecerIzq {
    from { opacity: 0; transform: translateX(-50px); }
    to { opacity: 1; transform: translateX(0); }
}

@keyframes aparecerDer {
    from { opacity: 0; transform: translateX(50px); }
    to { opacity: 1; transform: translateX(0); }
}

/* CONTENIDO */
.contenido {
    max-width: 400px;
}

/* TEXTO */
h1 {
    margin-bottom: 15px;
}

p {
    margin-bottom: 20px;
}

/* IMAGEN */
img {
    width: 260px;
    margin-bottom: 20px;
    animation: flotar 3s infinite ease-in-out;
}

@keyframes flotar {
    0%,100% { transform: translateY(0); }
    50% { transform: translateY(-10px); }
}

/* FORM */
.card {
    background: white;
    color: black;
    padding: 30px;
    border-radius: 15px;
    width: 300px;
    box-shadow: 0px 10px 25px rgba(0,0,0,0.2);
    animation: fadeUp 1s ease;
}

@keyframes fadeUp {
    from { opacity: 0; transform: translateY(40px); }
    to { opacity: 1; transform: translateY(0); }
}

/* INPUTS */
input, select {
    width: 100%;
    padding: 10px;
    margin: 8px 0;
    border-radius: 8px;
    border: none;
    background: #f1f1f1;
    transition: 0.3s;
}

input:focus, select:focus {
    transform: scale(1.05);
    outline: none;
}

/* BOTONES */
button {
    width: 100%;
    padding: 10px;
    margin-top: 10px;
    border-radius: 8px;
    border: none;
    cursor: pointer;
    transition: 0.3s;
}

/* BOTÓN PRINCIPAL */
.btn-registro {
    background: #2b7cff;
    color: white;
}

.btn-registro:hover {
    transform: scale(1.05);
}

/* BOTÓN LOGIN */
.btn-login {
    background: transparent;
    border: 2px solid #2b7cff;
    color: #2b7cff;
    margin-top: 10px;
}

.btn-login:hover {
    background: #2b7cff;
    color: white;
}

/* LOGO */
.logo {
    position: absolute;
    top: 20px;
    left: 20px;
    font-weight: bold;
    animation: fadeIn 1.5s;
}

@keyframes fadeIn {
    from { opacity: 0; }
    to { opacity: 1; }
}
</style>

</head>

<body>

<div class="logo">Caboré</div>

<!-- IZQUIERDA -->
<div class="seccion izquierda">
    <div class="contenido">
        <img src="img/tienda.png">
        <h1>Crea y vende fácilmente</h1>
        <p>Regístrate y empieza a usar la plataforma</p>
    </div>
</div>

<!-- DERECHA -->
<div class="seccion derecha">
    <div class="card">
        <h2>Registro</h2>

        <form action="registro.php" method="POST">
            <input type="text" name="nombre" placeholder="Nombre" required>
            <input type="email" name="correo" placeholder="Correo" required>
            <input type="password" name="contrasena" placeholder="Contraseña" required>

            <select name="rol">
                <option value="cliente">Cliente</option>
                <option value="emprendedor">Emprendedor</option>
            </select>

            <button class="btn-registro" type="submit">Registrarse</button>
        </form>

        <!-- BOTÓN LOGIN -->
        <button class="btn-login" onclick="irLogin()">Ya tengo cuenta</button>
    </div>
</div>

<script>
function irLogin() {
    window.location.href = "index.html";
}
</script>

</body>
</html>