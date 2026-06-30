<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Caboré — Crear cuenta</title>
<link href="https://fonts.googleapis.com/css2?family=Playfair+Display:wght@700;900&family=DM+Sans:wght@300;400;500;600&display=swap" rel="stylesheet">
<style>
:root {
    --gold:   #c9a84c;
    --dark:   #0d0d14;
    --light:  #f5f0e8;
    --cream:  #ede8dc;
    --glass:  rgba(255,255,255,0.06);
    --border: rgba(201,168,76,0.25);
}

*, *::before, *::after { margin:0; padding:0; box-sizing:border-box; }

body {
    font-family: 'DM Sans', sans-serif;
    background: var(--dark);
    color: var(--light);
    min-height: 100vh;
    display: flex;
    position: relative;
    overflow: hidden;
}

.bg-grid {
    position: fixed; inset: 0;
    background-image:
        linear-gradient(rgba(201,168,76,0.04) 1px, transparent 1px),
        linear-gradient(90deg, rgba(201,168,76,0.04) 1px, transparent 1px);
    background-size: 60px 60px;
    pointer-events: none;
}

.bg-glow {
    position: fixed;
    width: 500px; height: 500px;
    border-radius: 50%;
    filter: blur(120px);
    opacity: 0.12;
    pointer-events: none;
}
.bg-glow.a { background: #c9a84c; top: -100px; right: -100px; }
.bg-glow.b { background: #6366f1; bottom: -100px; left: -100px; }

/* LAYOUT */
.page {
    display: flex;
    width: 100%;
    min-height: 100vh;
    z-index: 1;
}

/* HERO IZQUIERDA */
.hero {
    flex: 1;
    display: flex;
    flex-direction: column;
    justify-content: center;
    padding: 80px 60px;
    position: relative;
}

.logo {
    font-family: 'Playfair Display', serif;
    font-size: 30px;
    font-weight: 900;
    color: var(--gold);
    margin-bottom: 80px;
}
.logo span { color: var(--light); }

.hero-tag {
    display: inline-block;
    padding: 5px 14px;
    border: 1px solid var(--border);
    border-radius: 20px;
    font-size: 12px;
    font-weight: 500;
    color: var(--gold);
    letter-spacing: 1px;
    text-transform: uppercase;
    margin-bottom: 24px;
}

.hero h1 {
    font-family: 'Playfair Display', serif;
    font-size: clamp(36px, 4vw, 56px);
    font-weight: 900;
    line-height: 1.1;
    color: var(--light);
    margin-bottom: 20px;
}

.hero h1 em {
    font-style: normal;
    color: var(--gold);
}

.hero p {
    color: rgba(245,240,232,0.5);
    font-size: 17px;
    font-weight: 300;
    line-height: 1.7;
    max-width: 420px;
    margin-bottom: 40px;
}

.features {
    display: flex;
    flex-direction: column;
    gap: 16px;
}

.feature {
    display: flex;
    align-items: center;
    gap: 14px;
    color: rgba(245,240,232,0.6);
    font-size: 15px;
}

.feature-dot {
    width: 8px; height: 8px;
    background: var(--gold);
    border-radius: 50%;
    flex-shrink: 0;
}

/* FORMULARIO */
.form-side {
    width: 480px;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 60px 50px;
    border-left: 1px solid var(--border);
}

.form-box {
    width: 100%;
    animation: riseUp 0.8s cubic-bezier(0.22,1,0.36,1) both;
}

.form-box h2 {
    font-family: 'Playfair Display', serif;
    font-size: 28px;
    font-weight: 700;
    margin-bottom: 6px;
}

.form-box .subtitle {
    color: rgba(245,240,232,0.45);
    font-size: 14px;
    margin-bottom: 32px;
}

label {
    display: block;
    font-size: 13px;
    font-weight: 500;
    color: rgba(245,240,232,0.6);
    margin-bottom: 6px;
    letter-spacing: 0.3px;
}

.input-group { margin-bottom: 16px; }

input, select {
    width: 100%;
    padding: 13px 16px;
    background: rgba(255,255,255,0.05);
    border: 1px solid rgba(255,255,255,0.1);
    border-radius: 12px;
    color: var(--light);
    font-family: 'DM Sans', sans-serif;
    font-size: 15px;
    outline: none;
    transition: border-color 0.3s, background 0.3s;
    -webkit-appearance: none;
}

input::placeholder { color: rgba(245,240,232,0.25); }

input:focus, select:focus {
    border-color: var(--gold);
    background: rgba(201,168,76,0.06);
}

select option { background: #1a1a2e; color: var(--light); }

/* ROL SELECTOR */
.rol-selector {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10px;
    margin-bottom: 20px;
}

.rol-option {
    padding: 14px;
    border: 1px solid rgba(255,255,255,0.1);
    border-radius: 12px;
    cursor: pointer;
    text-align: center;
    transition: all 0.3s;
    position: relative;
}

.rol-option input[type="radio"] {
    position: absolute;
    opacity: 0;
    width: 0;
    height: 0;
}

.rol-option span {
    font-size: 22px;
    display: block;
    margin-bottom: 4px;
}

.rol-option p {
    font-size: 13px;
    color: rgba(245,240,232,0.5);
    margin: 0;
}

.rol-option:has(input:checked) {
    border-color: var(--gold);
    background: rgba(201,168,76,0.1);
}

.rol-option:has(input:checked) p {
    color: var(--gold);
}

/* BTN */
.btn-submit {
    width: 100%;
    padding: 15px;
    background: linear-gradient(135deg, var(--gold), #a8832a);
    color: #0d0d14;
    border: none;
    border-radius: 12px;
    font-family: 'DM Sans', sans-serif;
    font-size: 15px;
    font-weight: 700;
    cursor: pointer;
    transition: all 0.3s;
    letter-spacing: 0.3px;
    margin-top: 8px;
}

.btn-submit:hover {
    transform: translateY(-2px);
    box-shadow: 0 12px 32px rgba(201,168,76,0.35);
}

.form-footer {
    text-align: center;
    margin-top: 20px;
    font-size: 13px;
    color: rgba(245,240,232,0.4);
}

.form-footer a {
    color: var(--gold);
    text-decoration: none;
    font-weight: 500;
}
.form-footer a:hover { text-decoration: underline; }

@keyframes riseUp {
    from { opacity:0; transform: translateY(30px); }
    to   { opacity:1; transform: translateY(0); }
}

.floating-img {
    position: absolute;
    right: -40px; top: 50%;
    transform: translateY(-50%);
    font-size: 180px;
    opacity: 0.07;
    pointer-events: none;
    animation: flotar 4s infinite ease-in-out;
}

@keyframes flotar {
    0%,100% { transform: translateY(-50%); }
    50%      { transform: translateY(calc(-50% - 12px)); }
}
</style>
</head>
<body>
<div class="bg-grid"></div>
<div class="bg-glow a"></div>
<div class="bg-glow b"></div>

<div class="page">
    <div class="hero">
        <div class="logo">Cabo<span>ré</span></div>

        <div class="hero-tag">Marketplace colombiano</div>
        <h1>Compra. Vende.<br><em>Crece.</em></h1>
        <p>La plataforma que conecta emprendedores locales con clientes que valoran lo auténtico. Crea tu tienda en minutos.</p>

        <div class="features">
            <div class="feature">
                <div class="feature-dot"></div>
                Tu tienda personalizada con tu logo y colores
            </div>
            <div class="feature">
                <div class="feature-dot"></div>
                Gestión de productos simple y rápida
            </div>
            <div class="feature">
                <div class="feature-dot"></div>
                Carrito de compras integrado para tus clientes
            </div>
            <div class="feature">
                <div class="feature-dot"></div>
                Catálogo público accesible desde cualquier lugar
            </div>
        </div>

        <div class="floating-img">🛍️</div>
    </div>

    <div class="form-side">
        <div class="form-box">
            <h2>Crear cuenta</h2>
            <p class="subtitle">Es gratis. Siempre.</p>

            <form action="registro.php" method="POST">
                <div class="input-group">
                    <label>Nombre completo</label>
                    <input type="text" name="nombre" placeholder="Tu nombre" required>
                </div>

                <div class="input-group">
                    <label>Correo electrónico</label>
                    <input type="email" name="correo" placeholder="tu@correo.com" required>
                </div>

                <div class="input-group">
                    <label>Contraseña</label>
                    <input type="password" name="contrasena" placeholder="Mínimo 6 caracteres" required minlength="6">
                </div>

                <div class="input-group">
                    <label>¿Cómo usarás Caboré?</label>
                    <div class="rol-selector">
                        <label class="rol-option">
                            <input type="radio" name="rol" value="cliente" checked>
                            <span>🛒</span>
                            <strong>Cliente</strong>
                            <p>Quiero comprar</p>
                        </label>
                        <label class="rol-option">
                            <input type="radio" name="rol" value="emprendedor">
                            <span>🚀</span>
                            <strong>Emprendedor</strong>
                            <p>Quiero vender</p>
                        </label>
                    </div>
                </div>

                <button type="submit" class="btn-submit">Crear mi cuenta →</button>
            </form>

            <div class="form-footer">
                ¿Ya tienes cuenta? <a href="login.html">Inicia sesión</a>
            </div>
        </div>
    </div>
</div>
</body>
</html>