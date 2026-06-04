/* ============================================================
   Ô Ăn Quan – game.js v7
   Luật đúng: rải tiếp khi rơi vào ô có quân, ăn dây chuyền.
   ============================================================ */

const gameId  = Number(window.location.pathname.split('/').pop());
const params  = new URLSearchParams(window.location.search);

let state        = null;
let selectedCell = null;
let stompClient  = null;
let roomSubscription = null;
let roomSubscriptionCode = null;
let reconnectSocketTimer = null;
let animating    = false;
let pendingTimer = null;
let aiTimer      = null;
let resultShownFor = null;

/* Timing (ms) */
const T_SOW     = 480;
const T_SETTLE  = 65;
const T_PICKUP  = 200;
const T_CAPTURE = 640;
const T_AI      = 900;
const WS_ENDPOINT = '/ws';

let mySide     = params.get('side')  || localStorage.getItem(`gameSide_${gameId}`)  || '';
let playerName = params.get('name')  || localStorage.getItem(`playerName_${gameId}`) || '';
if (mySide)     localStorage.setItem(`gameSide_${gameId}`, mySide);
if (playerName) localStorage.setItem(`playerName_${gameId}`, playerName);

function effectiveSide() {
  if (state && state.aiGame) return 'A';
  return (state && state.roomCode) ? mySide : '';
}

function toEngineDir(screenDir) {
  return (effectiveSide() === 'B')
      ? (screenDir === 'LEFT' ? 'RIGHT' : 'LEFT')
      : screenDir;
}

function aiLevelLabel() {
  return (state && state.aiDifficultyLabel) ? state.aiDifficultyLabel : 'Trung bình';
}

function aiModeText() {
  return 'Chơi với AI ' + aiLevelLabel();
}

/* ── Bootstrap ── */
window.addEventListener('DOMContentLoaded', () => {
  loadGame(false);
  loadHistory();
  connectSocket();
});

/* ── Data loading ── */
async function loadGame(animate = false) {
  try {
    const res  = await fetch(`/api/games/${gameId}`);
    const data = await res.json();
    if (!res.ok) { showToast(data.message || 'Không tải được ván chơi'); return; }
    applyState(data, animate);
  } catch (e) { showToast('Lỗi mạng, thử tải lại trang.'); }
}

function applyState(newState, shouldAnimate = true) {
  if (pendingTimer) { clearTimeout(pendingTimer); pendingTimer = null; }
  if (aiTimer && !(newState.aiGame && newState.currentTurn === 'B' && newState.phase === 'PLAYING')) {
    clearTimeout(aiTimer); aiTimer = null;
  }

  const steps = newState.animationSteps || [];
  const canAnim = shouldAnimate && state && steps.length > 0 && !animating;

  if (!canAnim) {
    state = newState; selectedCell = null;
    ensureRoomSubscription();
    render(); if (newState.message) showToast(newState.message);
    triggerAi(); return;
  }

  const oldState = state;
  selectedCell = null;
  animating = true;
  setCtrlDisabled(true);
  updateBanner('Đang rải quân...', 'Theo dõi từng viên sỏi.', '🎲', 'animating');

  runAnimation(oldState, steps)
      .then(() => { state = newState; animating = false; ensureRoomSubscription(); render(); loadHistory(); if (newState.message) showToast(newState.message); triggerAi(); })
      .catch(err => { console.error(err); state = newState; animating = false; ensureRoomSubscription(); render(); loadHistory(); triggerAi(); });
}

/* ── Render ── */
function render() {
  // Scores
  el('scoreA').textContent = state.scoreA;
  el('scoreB').textContent = state.scoreB;

  const nameA = state.playerAUsername || 'Người A';
  const nameB = state.aiGame ? `${state.playerBUsername || 'Máy AI'} (${aiLevelLabel()})` : (state.playerBUsername || 'Người B');
  el('nameA').textContent = nameA;
  el('nameB').textContent = nameB;
  el('turnLabel').textContent = state.currentTurn;

  const side = effectiveSide();
  sc('scoreCardA').classList.toggle('active', state.currentTurn === 'A' && state.phase === 'PLAYING');
  sc('scoreCardB').classList.toggle('active', state.currentTurn === 'B' && state.phase === 'PLAYING');
  sc('scoreCardA').classList.toggle('mine', side === 'A');
  sc('scoreCardB').classList.toggle('mine', side === 'B');

  const total = Math.max(1, state.scoreA + state.scoreB);
  const pct   = state.scoreA === state.scoreB ? 50 : Math.max(8, Math.min(92, Math.round(state.scoreA / total * 100)));
  el('progressFill').style.width = pct + '%';

  const phaseText = {
    WAITING: 'Đang chờ',
    ENDED:   'Đã kết thúc',
    PLAYING: state.aiGame ? aiModeText() : (state.roomCode ? 'Online' : 'Chơi local')
  }[state.phase] || state.phase;
  el('phaseLabel').textContent = phaseText;

  el('roomCodeLabel').textContent = state.roomCode || (state.aiGame ? aiModeText() : 'Chơi local');
  el('sideLabel').textContent = side
      ? `Bạn là Người ${side} · hàng dưới là phía bạn`
      : 'Cả hai bên';

  updateNoticePill();
  updateBanner();
  renderBoard();
  updateButtons();
  updateHelper();
  updateBoardHint();
  updateResultModal();
  updateMsgBox();
}

function updateNoticePill() {
  const notice = el('turnNotice');
  if (!notice || !state) return;
  const side = effectiveSide();
  let txt = '', cls = '';
  if (state.phase === 'WAITING') { txt = 'Chờ người chơi'; cls = 'waiting'; }
  else if (state.phase === 'ENDED') { const r = resultInfo(); txt = r.summary; cls = 'ended'; }
  else if (state.aiGame && state.currentTurn === 'B') { txt = 'AI đang đi'; cls = 'ai'; }
  else if (side && side === state.currentTurn) { txt = 'Lượt bạn!'; cls = 'your-turn'; }
  else if (side && side !== state.currentTurn) { txt = 'Chờ đối thủ'; cls = 'waiting'; }
  else { txt = `Lượt ${state.currentTurn}`; cls = ''; }
  notice.className = 'msg-pill ' + cls;
  notice.textContent = txt;
}

function updateMsgBox() {
  const box = el('messageBox');
  if (!box || !state) return;
  const side = effectiveSide();
  let msg = state.message || '';
  if (!msg) {
    if (state.phase === 'WAITING') msg = `Phòng ${state.roomCode || ''}. Gửi mã này cho bạn bè hoặc chờ ghép trận.`;
    else if (state.phase === 'ENDED') msg = resultInfo().detail;
    else if (state.aiGame && state.currentTurn === 'B') msg = `Máy AI cấp ${aiLevelLabel()} đang chọn nước đi, bạn chờ một chút.`;
    else if (side && side !== state.currentTurn) msg = `Đang chờ đối thủ đi. Bàn tự cập nhật khi họ xong.`;
    else msg = 'Chọn ô dân sáng màu ở hàng của bạn.';
  }
  box.textContent = msg;
}

function updateBanner(customTitle, customSub, customIcon, customType) {
  const banner = el('turnBanner');
  if (!banner || !state) return;
  const titleEl = el('turnBannerTitle');
  const subEl   = el('turnBannerSub');
  const iconEl  = el('turnBannerIcon');
  const badgeEl = el('turnBadge');
  const side    = effectiveSide();

  let type, icon, title, sub, badge;

  if (customTitle) {
    type = customType || 'neutral'; icon = customIcon || '🎮';
    title = customTitle; sub = customSub || '';
    badge = customType === 'animating' ? 'Đang mô phỏng' : (state.phase || '');
  } else if (state.phase === 'WAITING') {
    type = 'waiting'; icon = '🕹️';
    title = 'Đang chờ đối thủ'; sub = `Mã phòng: ${state.roomCode || '---'}.`;
    badge = 'Chờ người chơi';
  } else if (state.phase === 'ENDED') {
    const r = resultInfo();
    type = r.type; icon = r.icon; title = r.title; sub = r.detail; badge = 'Kết thúc';
  } else if (state.aiGame && state.currentTurn === 'B') {
    type = 'ai'; icon = '🤖'; title = `Lượt của AI ${aiLevelLabel()}`; sub = 'Máy đang suy nghĩ, bạn chờ chút.'; badge = aiLevelLabel();
  } else if (side && side === state.currentTurn) {
    type = 'your-turn'; icon = '👆'; title = 'Tới lượt bạn!';
    sub = `Bạn là Người ${side}. Chọn ô sáng màu rồi chọn hướng rải.`; badge = 'Bạn đi';
  } else if (side && side !== state.currentTurn) {
    type = 'waiting'; icon = '⏳'; title = 'Chưa tới lượt bạn';
    sub = `Lượt Người ${state.currentTurn}. Bàn tự cập nhật khi đối thủ đi.`; badge = 'Chờ đối thủ';
  } else {
    type = 'neutral'; icon = state.currentTurn === 'A' ? '🔴' : '🔵';
    title = `Lượt Người ${state.currentTurn}`; sub = 'Chọn ô dân để đi.'; badge = `Lượt ${state.currentTurn}`;
  }

  banner.className = `turn-banner ${type}`;
  iconEl.textContent  = icon;
  titleEl.textContent = title;
  subEl.textContent   = sub;
  badgeEl.textContent = badge;
}


function updateBoardHint() {
  const hint = el('boardHint');
  if (!hint || !state) return;
  const side = effectiveSide();
  if (state.phase === 'ENDED') hint.textContent = resultInfo().summary;
  else if (state.phase === 'WAITING') hint.textContent = 'Chờ người chơi vào phòng';
  else if (animating) hint.textContent = 'Đang rải quân từng ô';
  else if (selectedCell !== null) hint.textContent = `Đã chọn ô ${selectedCell} · chọn hướng rải`;
  else if (state.aiGame && state.currentTurn === 'B') hint.textContent = `AI ${aiLevelLabel()} đang suy nghĩ`;
  else if (side && side !== state.currentTurn) hint.textContent = `Chờ Người ${state.currentTurn}`;
  else hint.textContent = `Lượt Người ${state.currentTurn} · chọn ô sáng`;
}

function updateHelper() {
  const bar = el('helperBar');
  if (!bar || !state) return;
  const side = effectiveSide();
  let cls = '', icon = '💡', txt = '';

  if (state.phase === 'ENDED') {
    const r = resultInfo(); cls = 'ended'; icon = r.icon; txt = r.title + ' — ' + r.detail;
  } else if (state.phase === 'WAITING') {
    cls = 'waiting'; icon = '🕹️'; txt = `Phòng ${state.roomCode || ''}. Gửi mã cho bạn bè hoặc chờ ghép trận.`;
  } else if (side && side !== state.currentTurn) {
    cls = 'waiting'; icon = '⏳'; txt = `Chưa tới lượt bạn. Đang chờ Người ${state.currentTurn}.`;
  } else if (state.aiGame && state.currentTurn === 'B') {
    cls = 'waiting'; icon = '🤖'; txt = `AI cấp ${aiLevelLabel()} đang đi, bạn không cần thao tác.`;
  } else if (selectedCell !== null) {
    const cell = state.board.find(c => c.index === selectedCell);
    cls = 'selected'; icon = '✅'; txt = `Đã chọn ô ${selectedCell} (${cell ? cell.dan : 0} quân). Bấm trái hoặc phải để rải.`;
  } else {
    txt = 'Chọn ô dân sáng màu ở hàng của bạn rồi chọn hướng rải.';
  }

  bar.className = 'helper-bar ' + cls;
  bar.querySelector('.helper-icon').textContent = icon;
  el('helperText').textContent = txt;
}

/* ── Board ── */
function boardLayout() {
  const side = effectiveSide();
  if (side === 'A') return {
    topLabel: '← Người B →', bottomLabel: '← Người A (bạn) →',
    top:    [11, 10, 9, 8, 7, 6, 5],
    bottom: [null, 0, 1, 2, 3, 4, null]
  };
  if (side === 'B') return {
    topLabel: '← Người A →', bottomLabel: '← Người B (bạn) →',
    top:    [11, 0, 1, 2, 3, 4, 5],
    bottom: [null, 10, 9, 8, 7, 6, null]
  };
  return {
    topLabel: '← Người A →', bottomLabel: '← Người B →',
    top:    [11, 0, 1, 2, 3, 4, 5],
    bottom: [null, 10, 9, 8, 7, 6, null]
  };
}

function renderBoard(boardData = null) {
  const boardEl = el('board');
  const src     = boardData || state.board;
  const map     = new Map(src.map(c => [c.index, c]));
  const layout  = boardLayout();

  // Bỏ wrapper board-row, render thẳng vào lưới 7 cột
  boardEl.innerHTML = `
    <div class="side-label top-label">${layout.topLabel}</div>
    ${renderCell(map.get(layout.top[0]))}
    ${layout.top.slice(1, 6).map(i => renderCell(map.get(i))).join('')}
    ${renderCell(map.get(layout.top[6]))}
    ${layout.bottom.slice(1, 6).map(i => renderCell(map.get(i))).join('')}
    <div class="side-label bottom-label">${layout.bottomLabel}</div>
  `;
}

function renderCell(cell) {
  if (!cell) return ''; // Không cần spacer nữa vì CSS Grid tự đẩy
  const sel  = canSelect(cell);
  const cls  = cellClasses(cell).join(' ');
  const aria = `${cell.index === 5 || cell.index === 11 ? 'Ô quan' : 'Ô dân'} ${cell.index}, ${cell.dan} quân${cell.quan ? ', có quan' : ''}${sel ? ', có thể chọn' : ''}`;
  return `<button type="button" class="${cls}" data-index="${cell.index}" onclick="selectCell(${cell.index})" aria-label="${aria}" ${sel ? '' : 'tabindex="-1"'}>${cellHtml(cell)}</button>`;
}

function cellClasses(cell) {
  const side = effectiveSide();
  const cls  = ['cell'];

  // Áp cứng class layout của lưới cho Quan Trái (11) và Quan Phải (5)
  if (cell.index === 11) cls.push('quan-left');
  if (cell.index === 5)  cls.push('quan-right');

  if (cell.quanIndex || cell.index === 11 || cell.index === 5) cls.push('quan');
  if (!cell.dan && !cell.quan) cls.push('empty');
  if (selectedCell === cell.index) cls.push('selected');
  if (canSelect(cell)) cls.push('can-select');
  if ((side === 'A' && cell.index <= 4) || (side === 'B' && cell.index >= 6 && cell.index <= 10)) cls.push('my-side-cell');

  return cls;
}

function cellHtml(cell) {
  const isQuan = cell.index === 11 || cell.index === 5;
  const quanClass = cell.index === 11 ? 'quan-green' : 'quan-red';
  const quanStone = cell.quan
      ? `<span class="stone quan-stone ${quanClass}" style="--x:50%;--y:52%;--r:${cell.index === 11 ? -8 : 7}deg;--s:1;--z:60"></span>`
      : '';

  return `
    <div class="cell-label">${isQuan ? 'Quan' : cell.index}</div>
    <div class="stone-layer" aria-hidden="true">
      ${quanStone}
      ${renderStones(cell.dan, cell.index)}
    </div>
    <div class="count-badge">${cell.dan}${cell.quan ? '+Q' : ''}</div>
  `;
}

function updateCellEl(index, boardData) {
  const cell = boardData.find(c => c.index === index);
  const node = document.querySelector(`.cell[data-index="${index}"]`);
  if (!cell || !node) return;
  node.className = cellClasses(cell).join(' ');
  node.innerHTML = cellHtml(cell);
}

function renderStones(count, idx) {
  const mobile = window.matchMedia && window.matchMedia('(max-width: 560px)').matches;
  const maxVisible = mobile ? 24 : 30;
  const visible = Math.min(count, maxVisible);
  let html = '';

  for (let i = 0; i < visible; i++) {
    const p = stonePos(i, idx, visible);
    const tone = pebbleTone(i, idx);
    html += `<span class="stone pebble-tone-${tone}" style="--x:${p.x}%;--y:${p.y}%;--r:${p.r}deg;--s:${p.s};--oval:${p.oval};--z:${p.z};--delay:${p.delay}ms"></span>`;
  }

  if (count > maxVisible) html += `<span class="more-stones">+${count - maxVisible}</span>`;
  return html;
}

function pebbleNoise(seed) {
  const x = Math.sin(seed * 12.9898 + 78.233) * 43758.5453;
  return x - Math.floor(x);
}

function pebbleTone(i, idx) {
  return 1 + Math.floor(pebbleNoise((idx + 1) * 71 + (i + 1) * 131) * 8);
}

function stonePos(i, idx, visible) {
  if (!visible) return { x: 50, y: 50, r: 0, s: 1, oval: 1, z: 1, delay: 0 };

  const seed = (idx + 1) * 97 + (i + 1) * 43;
  const angle = (i * 137.50776 + idx * 19 + pebbleNoise(seed) * 38) * Math.PI / 180;
  const radius = Math.sqrt((i + .55) / Math.max(visible, 1));
  const jitterX = (pebbleNoise(seed + 11) - .5) * 10;
  const jitterY = (pebbleNoise(seed + 23) - .5) * 8;

  const x = clamp(50 + Math.cos(angle) * radius * 34 + jitterX, 15, 85);
  const y = clamp(52 + Math.sin(angle) * radius * 25 + jitterY, 20, 82);

  return {
    x: Number(x.toFixed(2)),
    y: Number(y.toFixed(2)),
    r: Math.round((pebbleNoise(seed + 37) * 110) - 55),
    s: Number((0.82 + pebbleNoise(seed + 41) * 0.38).toFixed(2)),
    oval: Number((0.84 + pebbleNoise(seed + 53) * 0.36).toFixed(2)),
    z: Math.round(y),
    delay: Math.round(i * 12 + pebbleNoise(seed + 61) * 40)
  };
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

function canSelect(cell) {
  if (!state || state.phase !== 'PLAYING' || animating) return false;
  if (state.aiGame && state.currentTurn === 'B') return false;
  const side = effectiveSide();
  if (side && side !== state.currentTurn) return false;

  // HARD BLOCK: Cấm click bốc quân từ ô Quan (luôn là index 5 và 11)
  if (cell.index === 5 || cell.index === 11 || cell.quanIndex) return false;

  return (state.currentTurn === 'A' && cell.selectableA) ||
      (state.currentTurn === 'B' && cell.selectableB);
}

function selectCell(index) {
  if (!state) return;
  const cell = state.board.find(c => c.index === index);
  if (!cell || !canSelect(cell)) {
    if (state.phase === 'WAITING')  showToast('Ván chưa bắt đầu. Chờ người B vào phòng.');
    else if (state.phase === 'ENDED') showToast('Ván đã kết thúc.');
    else if (state.aiGame && state.currentTurn === 'B') showToast('Đang lượt AI, bạn chờ nhé.');
    else if (effectiveSide() && effectiveSide() !== state.currentTurn) showToast('Chưa tới lượt bạn.');
    else if (index === 5 || index === 11 || (cell && cell.quanIndex)) showToast('Luật chơi: Không được bốc quân từ ô Quan!');
    else showToast('Chọn ô dân sáng màu ở hàng của bạn (ô phải có quân).');
    return;
  }
  selectedCell = index;
  renderBoard(); updateButtons(); updateHelper();
}

/* ── Move ── */
async function makeMove(screenDir) {
  if (selectedCell === null) { showToast('Chọn ô dân trước rồi mới rải được.'); return; }
  const payload = { gameId, cellIndex: selectedCell, direction: toEngineDir(screenDir),
    playerSide: effectiveSide() || null, username: playerName || null };
  setCtrlDisabled(true);
  updateBanner('Đang gửi nước đi...', '', '⚡', 'animating');

  if (stompClient && stompClient.connected && state && state.roomCode) {
    stompClient.send('/app/game.move', {}, JSON.stringify(payload));
    selectedCell = null;
    pendingTimer = setTimeout(() => { setCtrlDisabled(false); loadGame(false); }, 12000);
    return;
  }

  try {
    const res  = await fetch(`/api/games/${gameId}/move`, { method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify(payload) });
    const data = await res.json();
    if (!res.ok) { showToast(data.message || 'Nước đi không hợp lệ'); setCtrlDisabled(false); render(); return; }
    applyState(data, true); loadHistory();
  } catch (e) { showToast('Lỗi mạng.'); setCtrlDisabled(false); }
}

function updateButtons() {
  const dis = !state || state.phase !== 'PLAYING' || animating || selectedCell === null
      || (state.aiGame && state.currentTurn === 'B')
      || (effectiveSide() && effectiveSide() !== state.currentTurn);
  setCtrlDisabled(dis);

  const left  = el('leftBtn');
  const right = el('rightBtn');
  if (!left || !right) return;
  if (selectedCell !== null) {
    left.querySelector('.btn-label').textContent  = `← Rải ô ${selectedCell} trái`;
    right.querySelector('.btn-label').textContent = `Rải ô ${selectedCell} phải →`;
  } else {
    left.querySelector('.btn-label').textContent  = 'Rải trái';
    right.querySelector('.btn-label').textContent = 'Rải phải';
  }
}

function setCtrlDisabled(v) {
  const l = el('leftBtn'), r = el('rightBtn');
  if (l) l.disabled = v;
  if (r) r.disabled = v;
}

/* ── Result modal ── */
function resultInfo() {
  if (!state) return { title: '...', detail: '', icon: '⏳', type: 'neutral', summary: '' };
  const side = effectiveSide();
  if (state.scoreA === state.scoreB) {
    return { title: 'Hòa!', detail: `Cả hai cùng ${state.scoreA} điểm.`, icon: '🤝', type: 'draw', summary: `Hòa ${state.scoreA}-${state.scoreB}` };
  }
  const wSide = state.scoreA > state.scoreB ? 'A' : 'B';
  const wName = wSide === 'A' ? (state.playerAUsername || 'Người A') : (state.playerBUsername || 'Người B');
  const youWon = side && side === wSide;
  return {
    title:   youWon ? 'Bạn thắng! 🎉' : (side ? 'Bạn thua!' : `${wName} thắng!`),
    detail:  `${wName} thắng · A ${state.scoreA} - ${state.scoreB} B`,
    icon:    youWon ? '🏆' : (side ? '😤' : '🏆'),
    type:    youWon ? 'win' : (side ? 'lose' : 'win'),
    summary: `A ${state.scoreA} - ${state.scoreB} B`
  };
}

function updateResultModal() {
  const modal = el('resultModal');
  if (!modal || !state) return;
  if (state.phase !== 'ENDED') { modal.classList.add('hidden'); return; }
  const r = resultInfo();
  el('resultIcon').textContent  = r.icon;
  el('resultTitle').textContent = r.title;
  el('resultDetail').textContent = r.detail;
  el('resultScoreA').textContent = state.scoreA;
  el('resultScoreB').textContent = state.scoreB;
  el('resultNameA').textContent  = state.playerAUsername || 'Người A';
  el('resultNameB').textContent  = state.playerBUsername || 'Người B';
  el('resultCard').className     = `result-card ${r.type}`;
  if (resultShownFor !== state.gameId) {
    resultShownFor = state.gameId;
    setTimeout(() => modal.classList.remove('hidden'), 500);
  }
}

/* ── AI ── */
function triggerAi() {
  if (!state || !state.aiGame || state.phase !== 'PLAYING' || state.currentTurn !== 'B' || animating) return;
  if (aiTimer) return;
  setCtrlDisabled(true);
  updateBanner(`Lượt của AI ${aiLevelLabel()}`, 'Máy đang tính nước đi...', '🤖', 'ai');
  aiTimer = setTimeout(async () => {
    aiTimer = null;
    try {
      const res  = await fetch(`/api/games/${gameId}/ai-move`, { method: 'POST' });
      const data = await res.json();
      if (!res.ok) { showToast(data.message || 'AI không đi được'); loadGame(false); return; }
      applyState(data, true); loadHistory();
    } catch (e) { showToast('Không gọi được AI. Kiểm tra server.'); loadGame(false); }
  }, T_AI);
}

/* ── History ── */
async function loadHistory() {
  try {
    const res   = await fetch(`/api/games/${gameId}/moves`);
    const moves = await res.json();
    const div   = el('history');
    if (!moves.length) { div.innerHTML = '<p style="color:var(--muted);font-size:13px">Chưa có nước đi nào.</p>'; return; }
    div.innerHTML = moves.slice().reverse().map(m => `
      <div class="move-line">
        #${m.moveOrder}: ô <b>${m.cellIndex}</b>, hướng <b>${m.direction === 'LEFT' ? '← trái' : 'phải →'}</b>, ăn <b>${m.capturedPoints}</b> điểm
      </div>`).join('');
  } catch (e) { /* ignore */ }
}

/* ── WebSocket ── */
function connectSocket() {
  if (typeof SockJS === 'undefined' || typeof Stomp === 'undefined') return;
  if (stompClient && stompClient.connected) return;

  const socket = new SockJS(WS_ENDPOINT);
  stompClient  = Stomp.over(socket);
  stompClient.debug = null;

  stompClient.connect({}, () => {
    if (reconnectSocketTimer) { clearTimeout(reconnectSocketTimer); reconnectSocketTimer = null; }

    stompClient.subscribe(`/topic/game/${gameId}`, msg => {
      try { applyState(JSON.parse(msg.body), true); loadHistory(); }
      catch (e) { loadGame(false); }
    });

    stompClient.subscribe(`/topic/game/${gameId}/errors`, msg => {
      if (pendingTimer) { clearTimeout(pendingTimer); pendingTimer = null; }
      try { showToast(JSON.parse(msg.body).message || 'Nước đi không hợp lệ'); }
      catch (e) { showToast('Nước đi không hợp lệ'); }
      setCtrlDisabled(false); loadGame(false);
    });

    ensureRoomSubscription();
  }, () => {
    console.warn('WebSocket lỗi, tạm dùng REST và tự kết nối lại.');
    if (!reconnectSocketTimer) reconnectSocketTimer = setTimeout(() => {
      reconnectSocketTimer = null;
      connectSocket();
    }, 3000);
  });
}

function ensureRoomSubscription() {
  if (!stompClient || !stompClient.connected || !state || !state.roomCode) return;
  if (roomSubscriptionCode === state.roomCode) return;

  if (roomSubscription) {
    try { roomSubscription.unsubscribe(); } catch (e) { /* ignore */ }
  }

  roomSubscriptionCode = state.roomCode;
  roomSubscription = stompClient.subscribe(`/topic/room/${state.roomCode}`, () => {
    loadGame(false);
  });
}

/* ── Animation ── */
async function runAnimation(oldState, steps) {
  const boardEl   = el('board');
  let tempBoard   = oldState.board.map(c => ({...c}));
  let curPickup   = null;
  boardEl.classList.add('is-animating');
  renderBoard(tempBoard);

  for (const step of [...steps].sort((a, b) => a.order - b.order)) {
    if (step.notice) el('messageBox').textContent = step.notice;

    if (step.action === 'PICKUP') {
      curPickup = step.pickupIndex ?? step.fromIndex;
      const src = tempBoard.find(c => c.index === curPickup);
      if (src) { src.dan = 0; src.quan = false; }
      updateCellEl(curPickup, tempBoard);
      mark(curPickup, 'pickup-cell', T_PICKUP + 100);
      await wait(T_PICKUP);

    } else if (step.action === 'SOW') {
      const pk = step.pickupIndex ?? step.fromIndex;
      if (curPickup !== pk) {
        curPickup = pk;
        const src = tempBoard.find(c => c.index === pk);
        if (src) { src.dan = 0; src.quan = false; }
        updateCellEl(pk, tempBoard);
        mark(pk, 'pickup-cell', T_PICKUP + 100);
        await wait(T_PICKUP);
      }
      mark(step.fromIndex, 'path-cell', T_SOW);
      await flyStone(step.fromIndex, step.toIndex);
      const tgt = tempBoard.find(c => c.index === step.toIndex);
      if (tgt) tgt.dan += 1;
      updateCellEl(step.toIndex, tempBoard);
      mark(step.toIndex, 'drop-hit', 200);
      await wait(T_SETTLE);

    } else if (step.action === 'CAPTURE') {
      curPickup = null;
      if (step.notice) el('messageBox').textContent = step.notice;
      mark(step.fromIndex, 'empty-bridge', T_CAPTURE);
      await pulseCapture(step.toIndex);
      const tgt = tempBoard.find(c => c.index === step.toIndex);
      if (tgt) { tgt.dan = 0; tgt.quan = false; }
      updateCellEl(step.toIndex, tempBoard);
      await wait(220);

    } else if (step.action === 'TURN_END') {
      curPickup = null;
      if (step.notice) el('messageBox').textContent = step.notice;
      mark(step.toIndex, 'turn-end-cell', 520);
      await wait(520);
    }
  }
  boardEl.classList.remove('is-animating');
}

function flyStone(fromIdx, toIdx) {
  const from = document.querySelector(`.cell[data-index="${fromIdx}"]`);
  const to   = document.querySelector(`.cell[data-index="${toIdx}"]`);
  if (!from || !to) return Promise.resolve();

  const fR = from.getBoundingClientRect(), tR = to.getBoundingClientRect();
  const seed = Math.round(performance.now()) + fromIdx * 97 + toIdx * 131;
  const tone = pebbleTone(seed % 17, toIdx);
  const endX = tR.left + tR.width  * (0.35 + pebbleNoise(seed + 7)  * 0.30);
  const endY = tR.top  + tR.height * (0.38 + pebbleNoise(seed + 13) * 0.30);

  const stone = document.createElement('span');
  stone.className = `flying-stone pebble-tone-${tone}`;
  stone.style.setProperty('--r', `${Math.round(pebbleNoise(seed + 19) * 100 - 50)}deg`);
  stone.style.setProperty('--s', `${(0.95 + pebbleNoise(seed + 23) * 0.16).toFixed(2)}`);
  document.body.appendChild(stone);

  const sx = fR.left + fR.width / 2, sy = fR.top + fR.height / 2;
  const dx = endX - sx, dy = endY - sy;
  const lift = Math.max(22, Math.min(46, Math.hypot(dx, dy) * .22));

  stone.style.left = `${sx}px`;
  stone.style.top = `${sy}px`;

  if (stone.animate) {
    const anim = stone.animate([
      { transform: 'translate3d(-50%,-50%,0) rotate(var(--r)) scale(.92)', opacity: 1 },
      { transform: `translate3d(calc(${dx*.52}px - 50%), calc(${dy*.52}px - 50% - ${lift}px), 0) rotate(calc(var(--r) + 92deg)) scale(1.12)`, opacity: 1, offset: .55 },
      { transform: `translate3d(calc(${dx}px - 50%), calc(${dy}px - 50%), 0) rotate(calc(var(--r) + 178deg)) scale(.96)`, opacity: 1 }
    ], { duration: T_SOW, easing: 'cubic-bezier(.18,.84,.24,1)', fill: 'forwards' });
    return anim.finished.catch(()=>{}).then(() => stone.remove());
  }

  stone.style.setProperty('--fly-duration', `${T_SOW}ms`);
  return new Promise(resolve => {
    requestAnimationFrame(() => {
      stone.style.transform = `translate3d(${dx}px, ${dy}px, 0) rotate(160deg) scale(.9)`;
      stone.style.opacity = '.3';
    });
    setTimeout(() => { stone.remove(); resolve(); }, T_SOW);
  });
}

function pulseCapture(index) {
  const cell = document.querySelector(`.cell[data-index="${index}"]`);
  if (!cell) return Promise.resolve();
  cell.classList.add('captured');
  spawnParticles(cell);
  return new Promise(resolve => setTimeout(() => { cell.classList.remove('captured'); resolve(); }, T_CAPTURE));
}

function spawnParticles(cell) {
  const rect = cell.getBoundingClientRect();
  for (let i = 0; i < 9; i++) {
    const p = document.createElement('span');
    p.className = 'capture-particle';
    const seed = rect.left + rect.top + i * 47;
    const angle = i * Math.PI * 2 / 9 + pebbleNoise(seed) * .35;
    const distance = 28 + pebbleNoise(seed + 9) * 26;
    p.style.left = `${rect.left + rect.width / 2}px`;
    p.style.top  = `${rect.top + rect.height / 2}px`;
    p.style.setProperty('--px', `${Math.cos(angle) * distance}px`);
    p.style.setProperty('--py', `${Math.sin(angle) * distance * .78}px`);
    p.style.setProperty('--r', `${Math.round(pebbleNoise(seed + 14) * 220 - 110)}deg`);
    document.body.appendChild(p);
    setTimeout(() => p.remove(), 720);
  }
}

function mark(index, cls, duration) {
  const node = document.querySelector(`.cell[data-index="${index}"]`);
  if (!node) return;
  node.classList.add(cls);
  setTimeout(() => node.classList.remove(cls), duration);
}

function wait(ms) { return new Promise(r => setTimeout(r, ms)); }

/* ── Toast ── */
function showToast(text) {
  const stack = el('toastStack');
  if (!stack || !text) return;
  const t = document.createElement('div');
  t.className = 'toast'; t.textContent = text;
  stack.appendChild(t);
  setTimeout(() => t.classList.add('show'), 20);
  setTimeout(() => { t.classList.remove('show'); setTimeout(() => t.remove(), 250); }, 3600);
}

/* ── Utils ── */
function el(id)  { return document.getElementById(id); }
function sc(id)  { return document.getElementById(id); }
/* ============================================================
   NAM-23130200 UI ENHANCEMENT PATCH
   Thêm class trạng thái body + ripple + rung nhẹ trên mobile.
   Không sửa luật chơi, không sửa API.
   ============================================================ */
(function () {
  const oldRender = render;
  render = function () {
    oldRender();
    namDecorateState();
  };

  const oldSelectCell = selectCell;
  selectCell = function (index) {
    oldSelectCell(index);
    if (navigator.vibrate && selectedCell === index) navigator.vibrate(18);
  };

  document.addEventListener('pointerdown', function (event) {
    const target = event.target.closest('.btn, .move-btn, .reload-btn, .cell.can-select');
    if (!target || target.disabled) return;
    namRipple(target, event.clientX, event.clientY);
  }, { passive: true });

  function namDecorateState() {
    if (!state) return;
    const side = effectiveSide();
    document.body.classList.toggle('is-my-turn', state.phase === 'PLAYING' && (!side || side === state.currentTurn) && !(state.aiGame && state.currentTurn === 'B'));
    document.body.classList.toggle('is-waiting', state.phase === 'WAITING' || (side && side !== state.currentTurn));
    document.body.classList.toggle('is-ended', state.phase === 'ENDED');
    document.body.dataset.turn = state.currentTurn || '';
  }

  function namRipple(target, x, y) {
    const oldPosition = getComputedStyle(target).position;
    if (oldPosition === 'static') target.style.position = 'relative';
    target.style.overflow = 'hidden';

    const rect = target.getBoundingClientRect();
    const dot = document.createElement('span');
    dot.className = 'ripple-dot';
    dot.style.left = `${x - rect.left}px`;
    dot.style.top = `${y - rect.top}px`;
    target.appendChild(dot);
    setTimeout(() => dot.remove(), 650);
  }
})();
