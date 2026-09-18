<?php
session_start();
include("conexion.php");

if (!isset($_SESSION['usuario_id'])) {
    header("Location: login.html"); exit();
}

$usuario_id = (int)$_SESSION['usuario_id'];
$rol        = $_SESSION['rol'] ?? '';

if (!in_array($rol, ['cliente', 'emprendedor'], true)) {
    header("Location: login.html"); exit();
}

// Nombre del usuario actual (para el avatar)
$stmtU = $conn->prepare("SELECT nombre FROM usuarios WHERE id = ?");
$stmtU->bind_param("i", $usuario_id);
$stmtU->execute();
$miNombre = $stmtU->get_result()->fetch_assoc()['nombre'] ?? 'Usuario';

// Si es emprendedor, necesitamos su tienda
$tienda_id_propia = null;
if ($rol === 'emprendedor') {
    $stmtT = $conn->prepare("SELECT id FROM tiendas WHERE usuario_id = ?");
    $stmtT->bind_param("i", $usuario_id);
    $stmtT->execute();
    $tienda_id_propia = $stmtT->get_result()->fetch_assoc()['id'] ?? null;
}

// ── Lista de conversaciones del usuario actual ──────────────
if ($rol === 'cliente') {
    $stmtConv = $conn->prepare("
        SELECT c.id, c.actualizado_en,
               t.nombre AS nombre_otro, t.logo AS logo_otro,
               (SELECT contenido FROM mensajes WHERE conversacion_id = c.id ORDER BY id DESC LIMIT 1) AS ultimo_mensaje,
               (SELECT COUNT(*) FROM mensajes WHERE conversacion_id = c.id AND emisor_id != ? AND leido = 0) AS no_leidos
        FROM conversaciones c
        JOIN tiendas t ON t.id = c.tienda_id
        WHERE c.cliente_id = ?
        ORDER BY c.actualizado_en DESC
    ");
    $stmtConv->bind_param("ii", $usuario_id, $usuario_id);
} else {
    $stmtConv = $conn->prepare("
        SELECT c.id, c.actualizado_en,
               u.nombre AS nombre_otro, NULL AS logo_otro,
               (SELECT contenido FROM mensajes WHERE conversacion_id = c.id ORDER BY id DESC LIMIT 1) AS ultimo_mensaje,
               (SELECT COUNT(*) FROM mensajes WHERE conversacion_id = c.id AND emisor_id != ? AND leido = 0) AS no_leidos
        FROM conversaciones c
        JOIN usuarios u ON u.id = c.cliente_id
        WHERE c.tienda_id = ?
        ORDER BY c.actualizado_en DESC
    ");
    $stmtConv->bind_param("ii", $usuario_id, $tienda_id_propia);
}
$stmtConv->execute();
$conversaciones = $stmtConv->get_result()->fetch_all(MYSQLI_ASSOC);

// ── Conversación activa ──────────────────────────────────────
$conversacion_id    = isset($_GET['conversacion_id']) ? (int)$_GET['conversacion_id'] : 0;
$conversacionActiva = null;

foreach ($conversaciones as $c) {
    if ((int)$c['id'] === $conversacion_id) { $conversacionActiva = $c; break; }
}
if (!$conversacionActiva && !empty($conversaciones)) {
    $conversacionActiva = $conversaciones[0];
    $conversacion_id    = (int)$conversacionActiva['id'];
}

$mensajes = [];

if ($conversacionActiva) {
    // Marcar como leídos los mensajes que no mandé yo
    $stmtRead = $conn->prepare("UPDATE mensajes SET leido = 1 WHERE conversacion_id = ? AND emisor_id != ? AND leido = 0");
    $stmtRead->bind_param("ii", $conversacion_id, $usuario_id);
    $stmtRead->execute();

    $stmtMsg = $conn->prepare("
        SELECT m.id, m.emisor_id, m.contenido, m.creado_en
        FROM mensajes m
        WHERE m.conversacion_id = ?
        ORDER BY m.id ASC
    ");
    $stmtMsg->bind_param("i", $conversacion_id);
    $stmtMsg->execute();
    $mensajes = $stmtMsg->get_result()->fetch_all(MYSQLI_ASSOC);
}

// Tema visual según el rol
$esEmprendedor = $rol === 'emprendedor';
$volverA       = $esEmprendedor ? 'emprendedor.php' : 'cliente.php';
?>
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Caboré — Mensajes</title>
<?php if ($esEmprendedor): ?>
<link href="https://fonts.googleapis.com/css2?family=Playfair+Display:wght@700;900&family=DM+Sans:wght@300;400;500;600&display=swap" rel="stylesheet">
<?php else: ?>
<link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700&family=Fraunces:ital,opsz,wght@0,9..144,300;0,9..144,600&display=swap" rel="stylesheet">
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css">
<?php endif; ?>
<style>
:root {
<?php if ($esEmprendedor): ?>
    --bg:      #faf7f0;
    --surface: #ffffff;
    --border:  rgba(184,146,60,0.28);
    --text:    #1c1810;
    --muted:   rgba(28,24,16,0.56);
    --accent:  #b8923c;
    --bubble-own: linear-gradient(135deg,#b8923c,#a8832a);
    --font-head: 'Playfair Display', serif;
    --font-body: 'DM Sans', sans-serif;
<?php else: ?>
    --bg:      #F7F5F2;
    --surface: #FFFFFF;
    --border:  #E8E4DF;
    --text:    #1A1612;
    --muted:   #7A736C;
    --accent:  #C85A2A;
    --bubble-own: #C85A2A;
    --font-head: 'Fraunces', Georgia, serif;
    --font-body: 'Plus Jakarta Sans', system-ui, sans-serif;
<?php endif; ?>
}
*,*::before,*::after{margin:0;padding:0;box-sizing:border-box;}
body{font-family:var(--font-body);background:var(--bg);color:var(--text);height:100vh;overflow:hidden;}

.topbar{
    height:60px;
    background:var(--surface);
    border-bottom:1px solid var(--border);
    display:flex;align-items:center;gap:14px;
    padding:0 20px;
}
.topbar a{color:var(--muted);text-decoration:none;font-size:14px;display:flex;align-items:center;gap:6px;}
.topbar a:hover{color:var(--accent);}
.topbar h1{font-family:var(--font-head);font-size:18px;font-weight:700;margin-left:6px;}

.chat-layout{display:flex;height:calc(100vh - 60px);}

/* Lista de conversaciones */
.conv-list{
    width:300px;flex-shrink:0;
    background:var(--surface);
    border-right:1px solid var(--border);
    overflow-y:auto;
}
.conv-item{
    display:flex;gap:12px;align-items:center;
    padding:14px 16px;
    border-bottom:1px solid var(--border);
    cursor:pointer;text-decoration:none;color:inherit;
    transition:background 0.15s;
}
.conv-item:hover{background:rgba(0,0,0,0.03);}
.conv-item.activa{background:rgba(184,146,60,0.08);}
.conv-avatar{
    width:42px;height:42px;border-radius:50%;
    background:linear-gradient(135deg,var(--accent),#00000030);
    color:#fff;display:flex;align-items:center;justify-content:center;
    font-weight:700;font-size:16px;flex-shrink:0;
}
.conv-info{flex:1;min-width:0;}
.conv-nombre{font-weight:600;font-size:14px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;}
.conv-preview{font-size:12.5px;color:var(--muted);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;}
.conv-badge{
    background:var(--accent);color:#fff;
    font-size:11px;font-weight:700;
    border-radius:20px;min-width:20px;height:20px;
    display:flex;align-items:center;justify-content:center;
    padding:0 6px;flex-shrink:0;
}
.conv-empty{padding:30px 20px;color:var(--muted);font-size:13.5px;text-align:center;}

/* Hilo de mensajes */
.thread{flex:1;display:flex;flex-direction:column;min-width:0;}
.thread-header{
    padding:16px 24px;background:var(--surface);
    border-bottom:1px solid var(--border);
    font-weight:700;font-family:var(--font-head);font-size:17px;
}
.thread-body{flex:1;overflow-y:auto;padding:24px;display:flex;flex-direction:column;gap:10px;}
.bubble{
    max-width:60%;
    padding:10px 14px;
    border-radius:16px;
    font-size:14px;
    line-height:1.4;
}
.bubble-otro{
    align-self:flex-start;
    background:var(--surface);
    border:1px solid var(--border);
    border-bottom-left-radius:4px;
}
.bubble-mio{
    align-self:flex-end;
    background:var(--bubble-own);
    color:#fff;
    border-bottom-right-radius:4px;
}
.bubble-hora{font-size:10.5px;opacity:0.65;margin-top:4px;display:block;}

.thread-empty{flex:1;display:flex;align-items:center;justify-content:center;color:var(--muted);font-size:14px;flex-direction:column;gap:10px;}
.thread-empty span{font-size:40px;}

.chat-input-bar{
    display:flex;gap:10px;
    padding:16px 24px;
    background:var(--surface);
    border-top:1px solid var(--border);
}
.chat-input-bar input[type=text]{
    flex:1;padding:12px 16px;
    border:1.5px solid var(--border);
    border-radius:24px;
    font-family:var(--font-body);
    font-size:14px;outline:none;
    background:var(--bg);
}
.chat-input-bar input[type=text]:focus{border-color:var(--accent);}
.chat-input-bar button{
    padding:0 22px;
    border:none;border-radius:24px;
    background:var(--accent);color:#fff;
    font-weight:700;cursor:pointer;
    font-family:var(--font-body);font-size:14px;
}
</style>
</head>
<body>

<div class="topbar">
    <a href="<?= $volverA ?>">← Volver</a>
    <h1>💬 Mensajes</h1>
</div>

<div class="chat-layout">

    <div class="conv-list">
        <?php if (empty($conversaciones)): ?>
            <div class="conv-empty">
                <?= $esEmprendedor
                    ? 'Todavía no tienes conversaciones. Aparecerán aquí cuando un cliente te escriba desde tu tienda pública.'
                    : 'Todavía no has contactado ninguna tienda. Entra a una tienda pública y dale a "Contactar tienda".' ?>
            </div>
        <?php else: ?>
            <?php foreach ($conversaciones as $c): ?>
                <a href="chat.php?conversacion_id=<?= $c['id'] ?>"
                   class="conv-item <?= (int)$c['id'] === (int)$conversacion_id ? 'activa' : '' ?>">
                    <div class="conv-avatar"><?= strtoupper(substr($c['nombre_otro'], 0, 1)) ?></div>
                    <div class="conv-info">
                        <div class="conv-nombre"><?= htmlspecialchars($c['nombre_otro']) ?></div>
                        <div class="conv-preview"><?= htmlspecialchars($c['ultimo_mensaje'] ?? 'Sin mensajes todavía') ?></div>
                    </div>
                    <?php if ($c['no_leidos'] > 0): ?>
                        <div class="conv-badge"><?= $c['no_leidos'] ?></div>
                    <?php endif; ?>
                </a>
            <?php endforeach; ?>
        <?php endif; ?>
    </div>

    <div class="thread">
        <?php if (!$conversacionActiva): ?>
            <div class="thread-empty">
                <span>💬</span>
                <p>Selecciona una conversación para empezar a chatear.</p>
            </div>
        <?php else: ?>
            <div class="thread-header"><?= htmlspecialchars($conversacionActiva['nombre_otro']) ?></div>

            <div class="thread-body" id="thread-body">
                <?php if (empty($mensajes)): ?>
                    <div class="thread-empty">
                        <span>👋</span>
                        <p>Empieza la conversación escribiendo un mensaje.</p>
                    </div>
                <?php else: ?>
                    <?php foreach ($mensajes as $m): ?>
                        <div class="bubble <?= (int)$m['emisor_id'] === $usuario_id ? 'bubble-mio' : 'bubble-otro' ?>">
                            <?= nl2br(htmlspecialchars($m['contenido'])) ?>
                            <span class="bubble-hora"><?= date('d/m H:i', strtotime($m['creado_en'])) ?></span>
                        </div>
                    <?php endforeach; ?>
                <?php endif; ?>
            </div>

            <form method="POST" action="enviar_mensaje.php" class="chat-input-bar">
                <input type="hidden" name="conversacion_id" value="<?= $conversacion_id ?>">
                <input type="text" name="contenido" placeholder="Escribe un mensaje…" autocomplete="off" required>
                <button type="submit">Enviar</button>
            </form>
        <?php endif; ?>
    </div>

</div>

<script>
// Desplaza el hilo hasta el último mensaje al cargar
const body = document.getElementById('thread-body');
if (body) body.scrollTop = body.scrollHeight;
</script>
</body>
</html>