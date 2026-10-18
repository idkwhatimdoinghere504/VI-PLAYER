/* ============================================================
   Chess — user interface
   ============================================================ */
(function () {
  'use strict';

  var C = window.Chess;
  var W = C.WHITE, B = C.BLACK;

  var GLYPH = { p: '\u265F', n: '\u265E', b: '\u265D', r: '\u265C', q: '\u265B', k: '\u265A' };

  var LEVELS = {
    easy:   { depth: 1, noise: 90 },
    medium: { depth: 2, noise: 14 },
    hard:   { depth: 3, noise: 0 }
  };

  /* ---------------------------------------------------- elements */

  var $ = function (id) { return document.getElementById(id); };

  var boardEl = $('board');
  var shellEl = boardEl.parentNode;
  var statusEl = $('status');
  var movesEl = $('moves');
  var thinkingEl = $('thinking');
  var promoModal = $('promoModal');
  var promoOptions = $('promoOptions');

  var modeSel = $('modeSel'), sideSel = $('sideSel'), levelSel = $('levelSel'),
      themeSel = $('themeSel');

  var playerEls = [
    { root: $('playerTop'),    name: $('nameTop'),    cap: $('capturedTop'),    av: $('avatarTop'),    sc: $('scoreTop') },
    { root: $('playerBottom'), name: $('nameBottom'), cap: $('capturedBottom'), av: $('avatarBottom'), sc: $('scoreBottom') }
  ];

  /* ---------------------------------------------------- state */

  var positions = [C.parseFen(C.START_FEN)];
  var sans = [];
  var ply = 0;

  var selected = -1;
  var displayMoves = [];
  var gameStatus = { over: false };
  var over = false;

  var flipped = false;
  var thinking = false;
  var aiToken = 0;

  var mode = 'ai';
  var playerColor = W;
  var level = 'medium';

  var drag = null;
  var hoverSq = -1;
  var promotion = null;

  /* ---------------------------------------------------- helpers */

  function cur() { return positions[ply]; }
  function live() { return ply === positions.length - 1; }

  function keysUpTo(n) {
    var keys = [];
    for (var i = 0; i <= n; i++) keys.push(C.repetitionKey(positions[i]));
    return keys;
  }

  function gameOver() { return C.status(positions[positions.length - 1], keysUpTo(positions.length - 1)); }

  function interactive() {
    return live() && !over && !thinking && !promotion &&
           (mode === 'human' || cur().turn === playerColor);
  }

  function squareFromPoint(clientX, clientY) {
    var rect = boardEl.getBoundingClientRect();
    var cell = rect.width / 8;
    var vf = Math.floor((clientX - rect.left) / cell);
    var vr = Math.floor((clientY - rect.top) / cell);
    if (vf < 0 || vf > 7 || vr < 0 || vr > 7) return -1;
    var f = flipped ? 7 - vf : vf;
    var r = flipped ? 7 - vr : vr;
    return C.sq(f, r);
  }

  /* ---------------------------------------------------- playing */

  function play(move) {
    var st = cur();
    var legal = live() ? displayMoves : C.legalMoves(st);
    var san = C.toSAN(st, move, legal);
    var ns = C.applyMove(st, move);

    positions = positions.slice(0, ply + 1);
    sans = sans.slice(0, ply);
    positions.push(ns);
    sans.push(san);
    ply = positions.length - 1;
    selected = -1;

    render();
    scheduleAI();
  }

  function tryMove(from, to) {
    var cands = [];
    for (var i = 0; i < displayMoves.length; i++) {
      var m = displayMoves[i];
      if (m.from === from && m.to === to) cands.push(m);
    }
    if (!cands.length) { selected = -1; render(); return false; }

    if (cands.length > 1) {
      promotion = { from: from, to: to, options: cands };
      openPromotion(cur().turn);
      return true;
    }
    selected = -1;
    play(cands[0]);
    return true;
  }

  function choosePromotion(type) {
    var p = promotion;
    promotion = null;
    promoModal.hidden = true;
    if (!p) return;
    for (var i = 0; i < p.options.length; i++) {
      if (p.options[i].promotion === type) { selected = -1; play(p.options[i]); return; }
    }
    render();
  }

  function cancelPromotion() {
    promotion = null;
    promoModal.hidden = true;
    render();
  }

  /* ---------------------------------------------------- AI */

  function aiToMove() {
    return mode === 'ai' && live() && !over && cur().turn !== playerColor;
  }

  function scheduleAI() {
    if (!aiToMove()) { setThinking(false); return; }
    setThinking(true);

    var token = ++aiToken;
    var snapshot = positions.length;

    setTimeout(function () {
      if (token !== aiToken) return;
      if (positions.length !== snapshot || !live() || !aiToMove()) {
        setThinking(false);
        return;
      }
      var cfg = LEVELS[level] || LEVELS.medium;
      var best = C.findBestMove(cur(), cfg.depth, cfg.noise);
      if (token !== aiToken) return;
      setThinking(false);
      if (best && aiToMove()) play(best);
      else render();
    }, 90);
  }

  function setThinking(on) {
    thinking = on;
    thinkingEl.hidden = !on;
    updateStatus();
    updateButtons();
  }

  /* ---------------------------------------------------- render */

  function render() {
    var st = cur();
    var lastIdx = positions.length - 1;
    gameStatus = C.status(positions[lastIdx], keysUpTo(lastIdx));
    over = gameStatus.over;

    displayMoves = live() ? C.legalMoves(st) : [];

    var checked = C.inCheck(st, st.turn);
    var kingSq = checked ? C.findKing(st.board, st.turn) : -1;
    var lm = st.lastMove;
    var canHint = selected >= 0 && live() && !over;

    var frag = document.createDocumentFragment();
    var vr, vf, r, f, i;

    for (vr = 0; vr < 8; vr++) {
      for (vf = 0; vf < 8; vf++) {
        r = flipped ? 7 - vr : vr;
        f = flipped ? 7 - vf : vf;
        i = C.sq(f, r);

        var d = document.createElement('div');
        d.className = 'sq ' + (((f + r) % 2 === 0) ? 'light' : 'dark');
        d.dataset.i = i;

        if (lm && (lm.from === i || lm.to === i)) d.classList.add('last');
        if (i === kingSq) d.classList.add('check');
        if (i === selected) d.classList.add('selected');

        var p = st.board[i];

        if (canHint) {
          for (var q = 0; q < displayMoves.length; q++) {
            var m = displayMoves[q];
            if (m.from === selected && m.to === i) {
              d.classList.add((m.enPassant || p !== C.EMPTY) ? 'hint-cap' : 'hint');
              break;
            }
          }
        }
        if (live() && !over && C.colorOf(p) === st.turn) d.classList.add('movable');

        if (p !== C.EMPTY) {
          var span = document.createElement('span');
          span.className = 'piece ' + (C.colorOf(p) === W ? 'w' : 'b');
          span.textContent = GLYPH[C.typeOf(p)];
          d.appendChild(span);
        }

        if (vf === 0) {
          var rc = document.createElement('span');
          rc.className = 'coord r';
          rc.textContent = String(8 - r);
          d.appendChild(rc);
        }
        if (vr === 7) {
          var fc = document.createElement('span');
          fc.className = 'coord f';
          fc.textContent = C.algebraic(i).charAt(0);
          d.appendChild(fc);
        }

        frag.appendChild(d);
      }
    }

    boardEl.innerHTML = '';
    boardEl.appendChild(frag);

    layout();
    updatePlayers();
    updateStatus();
    updateMoves();
    updateButtons();
  }

  function layout() {
    var w = boardEl.getBoundingClientRect().width;
    if (w) boardEl.style.setProperty('--sq', (w / 8) + 'px');
  }

  var REASON = {
    checkmate: 'Checkmate',
    stalemate: 'Stalemate',
    material: 'Insufficient material',
    fifty: 'Fifty-move rule',
    repetition: 'Threefold repetition'
  };

  function updateStatus() {
    var cls = '', main = '', sub = '';

    if (!live()) {
      main = 'Browsing history';
      sub = 'Jump to the latest move to continue';
    } else if (over) {
      if (gameStatus.reason === 'checkmate') {
        main = 'Checkmate';
        sub = (gameStatus.result === '1-0' ? 'White' : 'Black') + ' wins';
        cls = 'win';
      } else {
        main = 'Draw';
        sub = REASON[gameStatus.reason] || 'Drawn position';
      }
    } else if (thinking) {
      main = 'Thinking\u2026';
      sub = (cur().turn === W ? 'White' : 'Black') + ' is choosing a move';
    } else {
      var side = cur().turn === W ? 'White' : 'Black';
      main = side + ' to move';
      if (gameStatus.check) { sub = 'Check!'; cls = 'over'; }
    }

    statusEl.className = 'status' + (cls ? ' ' + cls : '');
    statusEl.innerHTML = '';
    var t = document.createElement('div');
    t.textContent = main;
    statusEl.appendChild(t);
    if (sub) {
      var s = document.createElement('span');
      s.className = 'sub';
      s.textContent = sub;
      statusEl.appendChild(s);
    }
  }

  function material(board) {
    var m = { w: 0, b: 0 };
    for (var i = 0; i < 64; i++) {
      var p = board[i];
      if (p === C.EMPTY) continue;
      var t = C.typeOf(p);
      if (t === 'k') continue;
      m[C.colorOf(p)] += C.VALUE[t];
    }
    return m;
  }

  function capturedGlyphs(board, color) {
    var opp = C.other(color);
    var want = { p: 8, n: 2, b: 2, r: 2, q: 1 };
    var have = { p: 0, n: 0, b: 0, r: 0, q: 0 };
    for (var i = 0; i < 64; i++) {
      var p = board[i];
      if (p === C.EMPTY || C.colorOf(p) !== opp) continue;
      have[C.typeOf(p)]++;
    }
    var order = ['q', 'r', 'b', 'n', 'p'], out = '';
    for (var k = 0; k < order.length; k++) {
      var t = order[k];
      var miss = want[t] - have[t];
      for (var j = 0; j < miss; j++) {
        out += '<span class="cap ' + (opp === W ? 'w' : 'b') + '">' + GLYPH[t] + '</span>';
      }
    }
    return out;
  }

  function updatePlayers() {
    var st = cur();
    var order = flipped ? [W, B] : [B, W];
    var mat = material(st.board);

    for (var k = 0; k < 2; k++) {
      var color = order[k];
      var el = playerEls[k];
      var label = color === W ? 'White' : 'Black';

      if (mode === 'ai') label = (color === playerColor) ? 'You' : 'Computer';

      el.name.textContent = label;
      el.av.innerHTML = color === W ? '&#9819;' : '&#9823;';
      el.cap.innerHTML = capturedGlyphs(st.board, color);

      var diff = (mat[color] - mat[C.other(color)]) / 100;
      el.sc.textContent = diff > 0 ? '+' + diff : '';

      el.root.classList.toggle('active', live() && !over && st.turn === color);
    }
  }

  function updateMoves() {
    if (!sans.length) {
      movesEl.innerHTML = '<div class="empty">No moves yet</div>';
      moveEls = [];
      return;
    }
    var html = '';
    for (var i = 0; i < sans.length; i += 2) {
      html += '<div class="num">' + (i / 2 + 1) + '.</div>';
      html += '<div class="mv' + (i + 1 === ply ? ' cur' : '') + '" data-ply="' + (i + 1) + '">' + sans[i] + '</div>';
      if (sans[i + 1] !== undefined) {
        html += '<div class="mv' + (i + 2 === ply ? ' cur' : '') + '" data-ply="' + (i + 2) + '">' + sans[i + 1] + '</div>';
      } else {
        html += '<div></div>';
      }
    }
    movesEl.innerHTML = html;
    var curEl = movesEl.querySelector('.mv.cur');
    if (curEl) curEl.scrollIntoView({ block: 'nearest' });
  }

  var moveEls = [];

  var navEls = {
    first: $('firstBtn'), prev: $('prevBtn'),
    next: $('nextBtn'), last: $('lastBtn'), undo: $('undoBtn')
  };

  function updateButtons() {
    var atStart = ply === 0;
    var atEnd = ply === positions.length - 1;
    navEls.first.disabled = atStart;
    navEls.prev.disabled = atStart;
    navEls.next.disabled = atEnd;
    navEls.last.disabled = atEnd;
    navEls.undo.disabled = sans.length === 0;
  }

  /* ---------------------------------------------------- ghost / drag */

  var ghostEl = document.createElement('div');
  ghostEl.className = 'ghost';
  ghostEl.hidden = true;
  shellEl.appendChild(ghostEl);

  function showGhost(i) {
    var p = cur().board[i];
    if (p === C.EMPTY) return;
    var cell = boardEl.getBoundingClientRect().width / 8;
    ghostEl.style.setProperty('--sq', cell + 'px');
    ghostEl.style.width = cell + 'px';
    ghostEl.style.height = cell + 'px';
    ghostEl.innerHTML = '<span class="piece ' + (C.colorOf(p) === W ? 'w' : 'b') + '">' + GLYPH[C.typeOf(p)] + '</span>';
    ghostEl.hidden = false;
    var orig = squareEl(i);
    if (orig) {
      var piece = orig.querySelector('.piece');
      if (piece) piece.style.opacity = '0';
    }
  }

  function moveGhost(clientX, clientY) {
    var rect = shellEl.getBoundingClientRect();
    ghostEl.style.left = (clientX - rect.left) + 'px';
    ghostEl.style.top = (clientY - rect.top) + 'px';
  }

  function hideGhost() {
    ghostEl.hidden = true;
    ghostEl.innerHTML = '';
  }

  function squareEl(i) {
    return boardEl.querySelector('.sq[data-i="' + i + '"]');
  }

  function clearHover() {
    if (hoverSq >= 0) {
      var el = squareEl(hoverSq);
      if (el) el.classList.remove('hover');
    }
    hoverSq = -1;
  }

  function hoverAt(clientX, clientY) {
    var i = squareFromPoint(clientX, clientY);
    var ok = false;
    if (i >= 0 && selected >= 0) {
      for (var k = 0; k < displayMoves.length; k++) {
        if (displayMoves[k].from === selected && displayMoves[k].to === i) { ok = true; break; }
      }
    }
    var target = ok ? i : -1;
    if (target === hoverSq) return;
    clearHover();
    hoverSq = target;
    if (target >= 0) {
      var el = squareEl(target);
      if (el) el.classList.add('hover');
    }
  }

  /* ---------------------------------------------------- input */

  function onDown(e) {
    if (!interactive()) return;
    if (e.button !== undefined && e.button !== 0) return;
    if (e.target === ghostEl) return;

    var i = squareFromPoint(e.clientX, e.clientY);
    if (i < 0) return;
    e.preventDefault();

    var st = cur();
    var mine = C.colorOf(st.board[i]) === st.turn;

    drag = {
      from: i, x: e.clientX, y: e.clientY, moved: false,
      mine: mine, prevSelected: selected
    };

    if (mine && selected !== i) {
      selected = i;
      render();
    }
    try { boardEl.setPointerCapture(e.pointerId); } catch (err) { /* ignore */ }
  }

  function onMove(e) {
    if (!drag) return;
    var dx = e.clientX - drag.x, dy = e.clientY - drag.y;

    if (!drag.moved) {
      if (dx * dx + dy * dy < 36) return;
      if (!drag.mine) return;
      if (selected !== drag.from) { selected = drag.from; render(); }
      drag.moved = true;
      showGhost(drag.from);
    }
    moveGhost(e.clientX, e.clientY);
    hoverAt(e.clientX, e.clientY);
  }

  function onUp(e) {
    if (!drag) return;
    var d = drag;
    drag = null;
    hideGhost();
    clearHover();

    if (d.moved) {
      var to = squareFromPoint(e.clientX, e.clientY);
      if (to >= 0 && to !== d.from) tryMove(d.from, to);
      else render();
      return;
    }

    if (d.mine) {
      if (d.prevSelected === d.from) { selected = -1; }
      render();
    } else if (d.prevSelected >= 0) {
      tryMove(d.prevSelected, d.from);
    } else {
      selected = -1;
      render();
    }
  }

  function onCancel() {
    drag = null;
    hideGhost();
    clearHover();
    render();
  }

  boardEl.addEventListener('pointerdown', onDown);
  boardEl.addEventListener('pointermove', onMove);
  boardEl.addEventListener('pointerup', onUp);
  boardEl.addEventListener('pointercancel', onCancel);
  boardEl.addEventListener('contextmenu', function (e) { e.preventDefault(); });

  /* ---------------------------------------------------- promotion modal */

  function openPromotion(color) {
    promoOptions.innerHTML = '';
    var order = ['q', 'r', 'b', 'n'];
    order.forEach(function (t) {
      var b = document.createElement('button');
      b.type = 'button';
      b.innerHTML = '<span class="piece ' + (color === W ? 'w' : 'b') + '" style="font-size:40px">' + GLYPH[t] + '</span>';
      b.addEventListener('click', function () { choosePromotion(t); });
      promoOptions.appendChild(b);
    });
    promoModal.hidden = false;
  }

  promoModal.addEventListener('click', function (e) {
    if (e.target === promoModal) cancelPromotion();
  });

  /* ---------------------------------------------------- navigation */

  function goTo(n) {
    n = Math.max(0, Math.min(positions.length - 1, n));
    if (n === ply) return;
    ply = n;
    selected = -1;
    render();
  }

  function undo() {
    if (!sans.length) return;
    aiToken++;
    setThinking(false);

    positions.pop();
    sans.pop();
    if (mode === 'ai') {
      while (sans.length && positions[positions.length - 1].turn !== playerColor) {
        positions.pop();
        sans.pop();
      }
    }
    ply = positions.length - 1;
    selected = -1;
    render();
    scheduleAI();
  }

  function newGame() {
    aiToken++;
    setThinking(false);
    positions = [C.parseFen(C.START_FEN)];
    sans = [];
    ply = 0;
    selected = -1;
    hoverSq = -1;
    promotion = null;
    promoModal.hidden = true;
    flipped = (playerColor === B);
    render();
    scheduleAI();
  }

  $('newBtn').addEventListener('click', newGame);
  $('undoBtn').addEventListener('click', undo);
  $('firstBtn').addEventListener('click', function () { goTo(0); });
  $('prevBtn').addEventListener('click', function () { goTo(ply - 1); });
  $('nextBtn').addEventListener('click', function () { goTo(ply + 1); });
  $('lastBtn').addEventListener('click', function () { goTo(positions.length - 1); });

  $('flipBtn').addEventListener('click', function () {
    flipped = !flipped;
    render();
  });

  movesEl.addEventListener('click', function (e) {
    var el = e.target.closest('.mv');
    if (!el) return;
    goTo(parseInt(el.dataset.ply, 10));
  });

  $('copyBtn').addEventListener('click', function () {
    var text = C.toFen(cur());
    var btn = this;
    function done() {
      var old = btn.textContent;
      btn.textContent = 'Copied!';
      setTimeout(function () { btn.textContent = old; }, 1200);
    }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(done, function () { window.prompt('FEN', text); });
    } else {
      window.prompt('FEN', text);
    }
  });

  modeSel.addEventListener('change', function () {
    mode = modeSel.value;
    render();
    scheduleAI();
  });
  sideSel.addEventListener('change', function () {
    playerColor = sideSel.value;
    flipped = (playerColor === B);
    render();
    scheduleAI();
  });
  levelSel.addEventListener('change', function () { level = levelSel.value; });
  themeSel.addEventListener('change', function () {
    document.documentElement.dataset.board = themeSel.value;
  });

  document.addEventListener('keydown', function (e) {
    if (e.target && /^(INPUT|SELECT|TEXTAREA)$/.test(e.target.tagName)) return;

    if (e.key === 'ArrowLeft') { goTo(ply - 1); e.preventDefault(); }
    else if (e.key === 'ArrowRight') { goTo(ply + 1); e.preventDefault(); }
    else if (e.key === 'ArrowUp') { goTo(0); e.preventDefault(); }
    else if (e.key === 'ArrowDown') { goTo(positions.length - 1); e.preventDefault(); }
    else if (e.key === 'Escape') {
      if (promotion) cancelPromotion();
      else { selected = -1; render(); }
    } else if (e.key === 'u' || e.key === 'U') { undo(); }
    else if (e.key === 'n' || e.key === 'N') { newGame(); }
    else if (e.key === 'f' || e.key === 'F') { flipped = !flipped; render(); }
  });

  window.addEventListener('resize', layout);

  if (window.ResizeObserver) new ResizeObserver(layout).observe(boardEl);

  /* ---------------------------------------------------- boot */

  render();
})();
