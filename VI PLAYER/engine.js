/* ============================================================
   Chess engine — pure rules + search, no DOM.
   Board is a 64-entry array of chars.
     index 0 = a8 ... index 7 = h8 ... index 63 = h1
     uppercase = white, lowercase = black, '.' = empty
   ============================================================ */
(function (global) {
  'use strict';

  /* ------------------------------------------------ constants */

  var WHITE = 'w', BLACK = 'b', EMPTY = '.', FILES = 'abcdefgh';

  var fileOf = function (i) { return i & 7; };
  var rankOf = function (i) { return i >> 3; };          // 0 = rank 8, 7 = rank 1
  var sq = function (f, r) { return r * 8 + f; };
  var algebraic = function (i) { return FILES[fileOf(i)] + (8 - rankOf(i)); };
  var fromAlgebraic = function (s) {
    return sq(FILES.indexOf(s[0]), 8 - parseInt(s[1], 10));
  };

  function colorOf(p) {
    if (p === EMPTY || p == null) return null;
    return p === p.toUpperCase() ? WHITE : BLACK;
  }
  function typeOf(p) { return p.toLowerCase(); }
  function other(c) { return c === WHITE ? BLACK : WHITE; }

  var KNIGHT_DELTAS = [[1,2],[2,1],[2,-1],[1,-2],[-1,-2],[-2,-1],[-2,1],[-1,2]];
  var KING_DELTAS   = [[0,1],[1,1],[1,0],[1,-1],[0,-1],[-1,-1],[-1,0],[-1,1]];
  var ORTHO         = [[0,1],[1,0],[0,-1],[-1,0]];
  var DIAG          = [[1,1],[1,-1],[-1,-1],[-1,1]];
  var ALL8          = ORTHO.concat(DIAG);

  var START_FEN = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';

  /* ------------------------------------------------ FEN */

  function parseFen(fen) {
    var parts = String(fen).trim().split(/\s+/);
    var board = new Array(64).fill(EMPTY);
    var i = 0;
    for (var n = 0; n < parts[0].length; n++) {
      var ch = parts[0][n];
      if (ch === '/') continue;
      if (ch >= '1' && ch <= '8') i += parseInt(ch, 10);
      else board[i++] = ch;
    }
    var castling = { K: false, Q: false, k: false, q: false };
    if (parts[2] && parts[2] !== '-') {
      for (var c = 0; c < parts[2].length; c++) castling[parts[2][c]] = true;
    }
    return {
      board: board,
      turn: parts[1] === 'b' ? BLACK : WHITE,
      castling: castling,
      ep: (!parts[3] || parts[3] === '-') ? -1 : fromAlgebraic(parts[3]),
      halfmove: parseInt(parts[4] || '0', 10),
      fullmove: parseInt(parts[5] || '1', 10)
    };
  }

  function toFen(state) {
    var rows = [];
    for (var r = 0; r < 8; r++) {
      var row = '', run = 0;
      for (var f = 0; f < 8; f++) {
        var p = state.board[sq(f, r)];
        if (p === EMPTY) { run++; continue; }
        if (run) { row += run; run = 0; }
        row += p;
      }
      if (run) row += run;
      rows.push(row);
    }
    var cs = (state.castling.K ? 'K' : '') + (state.castling.Q ? 'Q' : '') +
             (state.castling.k ? 'k' : '') + (state.castling.q ? 'q' : '');
    return rows.join('/') + ' ' + state.turn + ' ' + (cs || '-') + ' ' +
           (state.ep >= 0 ? algebraic(state.ep) : '-') + ' ' +
           state.halfmove + ' ' + state.fullmove;
  }

  /* ------------------------------------------------ attacks */

  function isAttacked(board, target, by) {
    var tf = fileOf(target), tr = rankOf(target), i, f, r, p;

    // pawns
    if (by === WHITE) {
      var pr = tr + 1;
      if (pr < 8) {
        for (i = -1; i <= 1; i += 2) {
          f = tf + i;
          if (f >= 0 && f < 8 && board[sq(f, pr)] === 'P') return true;
        }
      }
    } else {
      var br = tr - 1;
      if (br >= 0) {
        for (i = -1; i <= 1; i += 2) {
          f = tf + i;
          if (f >= 0 && f < 8 && board[sq(f, br)] === 'p') return true;
        }
      }
    }

    // knights
    for (i = 0; i < 8; i++) {
      f = tf + KNIGHT_DELTAS[i][0];
      r = tr + KNIGHT_DELTAS[i][1];
      if (f < 0 || f > 7 || r < 0 || r > 7) continue;
      p = board[sq(f, r)];
      if (p === (by === WHITE ? 'N' : 'n')) return true;
    }

    // king
    for (i = 0; i < 8; i++) {
      f = tf + KING_DELTAS[i][0];
      r = tr + KING_DELTAS[i][1];
      if (f < 0 || f > 7 || r < 0 || r > 7) continue;
      p = board[sq(f, r)];
      if (p === (by === WHITE ? 'K' : 'k')) return true;
    }

    // bishops / queens
    for (i = 0; i < 4; i++) {
      f = tf + DIAG[i][0]; r = tr + DIAG[i][1];
      while (f >= 0 && f < 8 && r >= 0 && r < 8) {
        p = board[sq(f, r)];
        if (p !== EMPTY) {
          if (colorOf(p) === by && (p === 'b' || p === 'B' || p === 'q' || p === 'Q')) return true;
          break;
        }
        f += DIAG[i][0]; r += DIAG[i][1];
      }
    }

    // rooks / queens
    for (i = 0; i < 4; i++) {
      f = tf + ORTHO[i][0]; r = tr + ORTHO[i][1];
      while (f >= 0 && f < 8 && r >= 0 && r < 8) {
        p = board[sq(f, r)];
        if (p !== EMPTY) {
          if (colorOf(p) === by && (p === 'r' || p === 'R' || p === 'q' || p === 'Q')) return true;
          break;
        }
        f += ORTHO[i][0]; r += ORTHO[i][1];
      }
    }

    return false;
  }

  function findKing(board, color) {
    var k = color === WHITE ? 'K' : 'k';
    for (var i = 0; i < 64; i++) if (board[i] === k) return i;
    return -1;
  }

  function inCheck(state, color) {
    var k = findKing(state.board, color);
    return k >= 0 && isAttacked(state.board, k, other(color));
  }

  /* ------------------------------------------------ move generation */

  function genPseudo(state) {
    var board = state.board, turn = state.turn, moves = [];
    var cast = state.castling;

    function add(from, to, extra) {
      var m = { from: from, to: to, piece: board[from], promotion: null,
                enPassant: false, doublePush: false, castle: null };
      if (extra) for (var k in extra) m[k] = extra[k];
      moves.push(m);
    }

    for (var i = 0; i < 64; i++) {
      var p = board[i];
      if (p === EMPTY || colorOf(p) !== turn) continue;

      var t = typeOf(p), f = fileOf(i), r = rankOf(i), df, dr, nf, nr, to, tp, d, k;

      if (t === 'p') {
        var dir = turn === WHITE ? -1 : 1;
        var startRank = turn === WHITE ? 6 : 1;
        var promoRank = turn === WHITE ? 0 : 7;
        var r1 = r + dir;

        if (r1 >= 0 && r1 < 8 && board[sq(f, r1)] === EMPTY) {
          to = sq(f, r1);
          if (r1 === promoRank) {
            add(i, to, { promotion: 'q' }); add(i, to, { promotion: 'r' });
            add(i, to, { promotion: 'b' }); add(i, to, { promotion: 'n' });
          } else {
            add(i, to);
            var r2 = r + 2 * dir;
            if (r === startRank && board[sq(f, r2)] === EMPTY) add(i, sq(f, r2), { doublePush: true });
          }
        }

        for (d = -1; d <= 1; d += 2) {
          nf = f + d; nr = r1;
          if (nf < 0 || nf > 7 || nr < 0 || nr > 7) continue;
          to = sq(nf, nr); tp = board[to];
          if (tp !== EMPTY && colorOf(tp) !== turn) {
            if (nr === promoRank) {
              add(i, to, { promotion: 'q' }); add(i, to, { promotion: 'r' });
              add(i, to, { promotion: 'b' }); add(i, to, { promotion: 'n' });
            } else add(i, to);
          } else if (tp === EMPTY && to === state.ep) {
            add(i, to, { enPassant: true });
          }
        }

      } else if (t === 'n') {
        for (k = 0; k < 8; k++) {
          nf = f + KNIGHT_DELTAS[k][0]; nr = r + KNIGHT_DELTAS[k][1];
          if (nf < 0 || nf > 7 || nr < 0 || nr > 7) continue;
          to = sq(nf, nr); tp = board[to];
          if (tp === EMPTY || colorOf(tp) !== turn) add(i, to);
        }

      } else if (t === 'k') {
        for (k = 0; k < 8; k++) {
          nf = f + KING_DELTAS[k][0]; nr = r + KING_DELTAS[k][1];
          if (nf < 0 || nf > 7 || nr < 0 || nr > 7) continue;
          to = sq(nf, nr); tp = board[to];
          if (tp === EMPTY || colorOf(tp) !== turn) add(i, to);
        }

        if (turn === WHITE && i === 60) {
          if (cast.K && board[63] === 'R' && board[61] === EMPTY && board[62] === EMPTY &&
              !isAttacked(board, 60, BLACK) && !isAttacked(board, 61, BLACK) && !isAttacked(board, 62, BLACK)) {
            add(i, 62, { castle: 'K' });
          }
          if (cast.Q && board[56] === 'R' && board[57] === EMPTY && board[58] === EMPTY && board[59] === EMPTY &&
              !isAttacked(board, 60, BLACK) && !isAttacked(board, 59, BLACK) && !isAttacked(board, 58, BLACK)) {
            add(i, 58, { castle: 'Q' });
          }
        } else if (turn === BLACK && i === 4) {
          if (cast.k && board[7] === 'r' && board[5] === EMPTY && board[6] === EMPTY &&
              !isAttacked(board, 4, WHITE) && !isAttacked(board, 5, WHITE) && !isAttacked(board, 6, WHITE)) {
            add(i, 6, { castle: 'K' });
          }
          if (cast.q && board[0] === 'r' && board[1] === EMPTY && board[2] === EMPTY && board[3] === EMPTY &&
              !isAttacked(board, 4, WHITE) && !isAttacked(board, 3, WHITE) && !isAttacked(board, 2, WHITE)) {
            add(i, 2, { castle: 'Q' });
          }
        }

      } else {
        var deltas = t === 'r' ? ORTHO : (t === 'b' ? DIAG : ALL8);
        for (k = 0; k < deltas.length; k++) {
          nf = f + deltas[k][0]; nr = r + deltas[k][1];
          while (nf >= 0 && nf < 8 && nr >= 0 && nr < 8) {
            to = sq(nf, nr); tp = board[to];
            if (tp === EMPTY) add(i, to);
            else {
              if (colorOf(tp) !== turn) add(i, to);
              break;
            }
            nf += deltas[k][0]; nr += deltas[k][1];
          }
        }
      }
    }
    return moves;
  }

  /* ------------------------------------------------ make move */

  function applyMove(state, move) {
    var board = state.board.slice();
    var turn = state.turn;
    var piece = board[move.from];
    var captured = board[move.to];
    var isCapture = captured !== EMPTY || move.enPassant;

    board[move.to] = move.promotion
      ? (turn === WHITE ? move.promotion.toUpperCase() : move.promotion)
      : piece;
    board[move.from] = EMPTY;

    if (move.enPassant) board[sq(fileOf(move.to), rankOf(move.from))] = EMPTY;

    if (move.castle) {
      if (move.castle === 'K') {
        var rf = turn === WHITE ? 63 : 7, rt = turn === WHITE ? 61 : 5;
        board[rt] = board[rf]; board[rf] = EMPTY;
      } else {
        var qf = turn === WHITE ? 56 : 0, qt = turn === WHITE ? 59 : 3;
        board[qt] = board[qf]; board[qf] = EMPTY;
      }
    }

    var cast = { K: state.castling.K, Q: state.castling.Q, k: state.castling.k, q: state.castling.q };
    if (piece === 'K') { cast.K = false; cast.Q = false; }
    if (piece === 'k') { cast.k = false; cast.q = false; }
    if (move.from === 63 || move.to === 63) cast.K = false;
    if (move.from === 56 || move.to === 56) cast.Q = false;
    if (move.from === 7  || move.to === 7)  cast.k = false;
    if (move.from === 0  || move.to === 0)  cast.q = false;

    return {
      board: board,
      turn: other(turn),
      castling: cast,
      ep: move.doublePush ? ((move.from + move.to) / 2) : -1,
      halfmove: (typeOf(piece) === 'p' || isCapture) ? 0 : state.halfmove + 1,
      fullmove: turn === BLACK ? state.fullmove + 1 : state.fullmove,
      lastMove: { from: move.from, to: move.to }
    };
  }

  /* ------------------------------------------------ legal moves */

  function legalMoves(state) {
    var pseudo = genPseudo(state), out = [];
    for (var i = 0; i < pseudo.length; i++) {
      var ns = applyMove(state, pseudo[i]);
      var k = findKing(ns.board, state.turn);
      if (k < 0 || !isAttacked(ns.board, k, other(state.turn))) out.push(pseudo[i]);
    }
    return out;
  }

  function movesFrom(state, from, legal) {
    var src = legal || legalMoves(state), out = [];
    for (var i = 0; i < src.length; i++) if (src[i].from === from) out.push(src[i]);
    return out;
  }

  /* ------------------------------------------------ game status */

  function insufficientMaterial(board) {
    var list = [], i;
    for (i = 0; i < 64; i++) if (board[i] !== EMPTY) list.push(i);
    if (list.length <= 2) return true;                                   // K vs K
    if (list.length === 3) {
      var t = typeOf(board[list.find(function (x) { return typeOf(board[x]) !== 'k'; })]);
      return t === 'n' || t === 'b';
    }
    if (list.length === 4) {
      var bs = list.filter(function (x) { return typeOf(board[x]) === 'b'; });
      if (bs.length === 2) {
        var c1 = (fileOf(bs[0]) + rankOf(bs[0])) % 2;
        var c2 = (fileOf(bs[1]) + rankOf(bs[1])) % 2;
        if (c1 === c2) return true;                                      // same-colour bishops
      }
    }
    return false;
  }

  function repetitionKey(state) {
    return state.board.join('') + state.turn +
           (state.castling.K ? 'K' : '') + (state.castling.Q ? 'Q' : '') +
           (state.castling.k ? 'k' : '') + (state.castling.q ? 'q' : '') +
           state.ep;
  }

  function status(state, historyKeys) {
    var moves = legalMoves(state);
    var checked = inCheck(state, state.turn);

    if (moves.length === 0) {
      return checked
        ? { over: true, check: true, result: state.turn === WHITE ? '0-1' : '1-0', reason: 'checkmate' }
        : { over: true, check: false, result: '1/2-1/2', reason: 'stalemate' };
    }
    if (state.halfmove >= 100)
      return { over: true, check: checked, result: '1/2-1/2', reason: 'fifty' };
    if (insufficientMaterial(state.board))
      return { over: true, check: checked, result: '1/2-1/2', reason: 'material' };

    if (historyKeys) {
      var key = repetitionKey(state), count = 0;
      for (var i = 0; i < historyKeys.length; i++) if (historyKeys[i] === key) count++;
      if (count >= 3) return { over: true, check: checked, result: '1/2-1/2', reason: 'repetition' };
    }

    return { over: false, check: checked, moves: moves };
  }

  /* ------------------------------------------------ SAN */

  function toSAN(state, move, legal) {
    var list = legal || legalMoves(state);
    var piece = state.board[move.from];
    var t = typeOf(piece);
    var san;

    if (move.castle) {
      san = move.castle === 'K' ? 'O-O' : 'O-O-O';
    } else if (t === 'p') {
      var cap = state.board[move.to] !== EMPTY || move.enPassant;
      san = (cap ? FILES[fileOf(move.from)] + 'x' : '') + algebraic(move.to);
      if (move.promotion) san += '=' + move.promotion.toUpperCase();
    } else {
      var cap2 = state.board[move.to] !== EMPTY;
      var disamb = '';
      var rivals = list.filter(function (m) {
        return m.to === move.to && m.from !== move.from &&
               typeOf(state.board[m.from]) === t;
      });
      if (rivals.length) {
        var sameFile = rivals.some(function (m) { return fileOf(m.from) === fileOf(move.from); });
        var sameRank = rivals.some(function (m) { return rankOf(m.from) === rankOf(move.from); });
        if (!sameFile) disamb = FILES[fileOf(move.from)];
        else if (!sameRank) disamb = String(8 - rankOf(move.from));
        else disamb = algebraic(move.from);
      }
      san = t.toUpperCase() + disamb + (cap2 ? 'x' : '') + algebraic(move.to);
    }

    var ns = applyMove(state, move);
    if (inCheck(ns, ns.turn)) san += legalMoves(ns).length === 0 ? '#' : '+';
    return san;
  }

  /* ------------------------------------------------ evaluation */

  var VALUE = { p: 100, n: 320, b: 330, r: 500, q: 900, k: 0 };

  var PST_P = [  0,  0,  0,  0,  0,  0,  0,  0,
                50, 50, 50, 50, 50, 50, 50, 50,
                10, 10, 20, 30, 30, 20, 10, 10,
                 5,  5, 10, 25, 25, 10,  5,  5,
                 0,  0,  0, 20, 20,  0,  0,  0,
                 5, -5,-10,  0,  0,-10, -5,  5,
                 5, 10, 10,-20,-20, 10, 10,  5,
                 0,  0,  0,  0,  0,  0,  0,  0 ];

  var PST_N = [ -50,-40,-30,-30,-30,-30,-40,-50,
                -40,-20,  0,  0,  0,  0,-20,-40,
                -30,  0, 10, 15, 15, 10,  0,-30,
                -30,  5, 15, 20, 20, 15,  5,-30,
                -30,  0, 15, 20, 20, 15,  0,-30,
                -30,  5, 10, 15, 15, 10,  5,-30,
                -40,-20,  0,  5,  5,  0,-20,-40,
                -50,-40,-30,-30,-30,-30,-40,-50 ];

  var PST_B = [ -20,-10,-10,-10,-10,-10,-10,-20,
                -10,  0,  0,  0,  0,  0,  0,-10,
                -10,  0,  5, 10, 10,  5,  0,-10,
                -10,  5,  5, 10, 10,  5,  5,-10,
                -10,  0, 10, 10, 10, 10,  0,-10,
                -10, 10, 10, 10, 10, 10, 10,-10,
                -10,  5,  0,  0,  0,  0,  5,-10,
                -20,-10,-10,-10,-10,-10,-10,-20 ];

  var PST_R = [   0,  0,  0,  0,  0,  0,  0,  0,
                  5, 10, 10, 10, 10, 10, 10,  5,
                 -5,  0,  0,  0,  0,  0,  0, -5,
                 -5,  0,  0,  0,  0,  0,  0, -5,
                 -5,  0,  0,  0,  0,  0,  0, -5,
                 -5,  0,  0,  0,  0,  0,  0, -5,
                 -5,  0,  0,  0,  0,  0,  0, -5,
                  0,  0,  0,  5,  5,  0,  0,  0 ];

  var PST_Q = [ -20,-10,-10, -5, -5,-10,-10,-20,
                -10,  0,  0,  0,  0,  0,  0,-10,
                -10,  0,  5,  5,  5,  5,  0,-10,
                 -5,  0,  5,  5,  5,  5,  0, -5,
                  0,  0,  5,  5,  5,  5,  0, -5,
                -10,  5,  5,  5,  5,  5,  0,-10,
                -10,  0,  5,  0,  0,  0,  0,-10,
                -20,-10,-10, -5, -5,-10,-10,-20 ];

  var PST_K_MID = [ -30,-40,-40,-50,-50,-40,-40,-30,
                    -30,-40,-40,-50,-50,-40,-40,-30,
                    -30,-40,-40,-50,-50,-40,-40,-30,
                    -30,-40,-40,-50,-50,-40,-40,-30,
                    -20,-30,-30,-40,-40,-30,-30,-20,
                    -10,-20,-20,-20,-20,-20,-20,-10,
                     20, 20,  0,  0,  0,  0, 20, 20,
                     20, 30, 10,  0,  0, 10, 30, 20 ];

  var PST_K_END = [ -50,-40,-30,-20,-20,-30,-40,-50,
                    -30,-20,-10,  0,  0,-10,-20,-30,
                    -30,-10, 20, 30, 30, 20,-10,-30,
                    -30,-10, 30, 40, 40, 30,-10,-30,
                    -30,-10, 30, 40, 40, 30,-10,-30,
                    -30,-10, 20, 30, 30, 20,-10,-30,
                    -30,-30,  0,  0,  0,  0,-30,-30,
                    -50,-30,-30,-30,-30,-30,-30,-50 ];

  var PST = { p: PST_P, n: PST_N, b: PST_B, r: PST_R, q: PST_Q };

  function evaluate(state) {
    var board = state.board, score = 0, npm = 0, i, p, t, idx;

    for (i = 0; i < 64; i++) {
      p = board[i];
      if (p === EMPTY) continue;
      t = typeOf(p);
      if (t === 'n' || t === 'b' || t === 'r' || t === 'q') npm += VALUE[t];
    }
    var endgame = npm <= 1600;

    for (i = 0; i < 64; i++) {
      p = board[i];
      if (p === EMPTY) continue;
      t = typeOf(p);
      var white = p === p.toUpperCase();
      var table = t === 'k' ? (endgame ? PST_K_END : PST_K_MID) : PST[t];
      idx = white ? i : (i ^ 56);
      var v = VALUE[t] + table[idx];
      score += white ? v : -v;
    }

    return state.turn === WHITE ? score : -score;
  }

  /* ------------------------------------------------ search */

  var MATE = 100000;

  function orderMoves(state, moves) {
    var board = state.board;
    for (var i = 0; i < moves.length; i++) {
      var m = moves[i], s = 0;
      var victim = board[m.to];
      if (victim !== EMPTY) s = 10 * VALUE[typeOf(victim)] - VALUE[typeOf(board[m.from])];
      else if (m.enPassant) s = 10 * VALUE.p - VALUE.p;
      if (m.promotion) s += VALUE[m.promotion];
      m.score = s;
    }
    moves.sort(function (a, b) { return (b.score || 0) - (a.score || 0); });
    return moves;
  }

  function quiesce(state, alpha, beta, ply) {
    var all = legalMoves(state);
    if (all.length === 0) {
      return inCheck(state, state.turn) ? -(MATE - ply) : 0;
    }

    var best = evaluate(state);
    if (best >= beta) return best;
    if (best > alpha) alpha = best;
    if (ply > 16) return best;

    var caps = all.filter(function (m) {
      return state.board[m.to] !== EMPTY || m.enPassant || m.promotion;
    });
    orderMoves(state, caps);

    for (var i = 0; i < caps.length; i++) {
      var ns = applyMove(state, caps[i]);
      var s = -quiesce(ns, -beta, -alpha, ply + 1);
      if (s > best) best = s;
      if (best > alpha) alpha = best;
      if (alpha >= beta) break;
    }
    return best;
  }

  function search(state, depth, alpha, beta, ply) {
    var moves = legalMoves(state);
    if (moves.length === 0) {
      return inCheck(state, state.turn) ? -(MATE - ply) : 0;
    }
    if (state.halfmove >= 100) return 0;
    if (depth <= 0) return quiesce(state, alpha, beta, ply);

    orderMoves(state, moves);
    var best = -Infinity;
    for (var i = 0; i < moves.length; i++) {
      var ns = applyMove(state, moves[i]);
      var s = -search(ns, depth - 1, -beta, -alpha, ply + 1);
      if (s > best) best = s;
      if (best > alpha) alpha = best;
      if (alpha >= beta) break;
    }
    return best;
  }

  function findBestMove(state, depth, noise) {
    var moves = legalMoves(state);
    if (moves.length === 0) return null;
    orderMoves(state, moves);

    var best = moves[0];

    for (var d = 1; d <= depth; d++) {
      var alpha = -Infinity, scored = [], localBest = -Infinity;
      for (var i = 0; i < moves.length; i++) {
        var ns = applyMove(state, moves[i]);
        var s = -search(ns, d - 1, -Infinity, -alpha, 1);
        scored.push({ m: moves[i], s: s });
        if (s > localBest) localBest = s;
        if (s > alpha) alpha = s;
      }

      var pickIdx = 0;
      if (noise > 0) {
        var cands = [];
        for (var j = 0; j < scored.length; j++) if (scored[j].s >= localBest - noise) cands.push(j);
        pickIdx = cands[Math.floor(Math.random() * cands.length)];
      } else {
        for (var k = 0; k < scored.length; k++) if (scored[k].s === localBest) { pickIdx = k; break; }
      }
      best = scored[pickIdx].m;

      scored.sort(function (a, b) { return b.s - a.s; });
      moves = scored.map(function (o) { return o.m; });
    }
    return best;
  }

  /* ------------------------------------------------ export */

  global.Chess = {
    WHITE: WHITE, BLACK: BLACK, EMPTY: EMPTY, MATE: MATE,
    START_FEN: START_FEN,
    VALUE: VALUE,
    fileOf: fileOf, rankOf: rankOf, sq: sq,
    algebraic: algebraic, fromAlgebraic: fromAlgebraic,
    colorOf: colorOf, typeOf: typeOf, other: other,
    parseFen: parseFen, toFen: toFen,
    isAttacked: isAttacked, findKing: findKing, inCheck: inCheck,
    genPseudo: genPseudo, legalMoves: legalMoves, movesFrom: movesFrom,
    applyMove: applyMove, toSAN: toSAN,
    status: status, repetitionKey: repetitionKey,
    insufficientMaterial: insufficientMaterial,
    evaluate: evaluate, findBestMove: findBestMove, search: search
  };

})(typeof window !== 'undefined' ? window : this);
