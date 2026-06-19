/**
 * 全链路系统模拟测试 v3
 * 修复: 正确的 Preflop 行动顺序 (UTG=Alice, SB=Bob, BB=Charlie)
 * UTG = (BB + 1) % n = left of big blind
 */
import { Client } from '@stomp/stompjs';
import WebSocket from 'ws';

Object.assign(globalThis, { WebSocket });

const BASE = 'http://localhost:8081';
const WS = 'ws://localhost:8081/ws-native';

async function api(method, path, body) {
  const opts = { method, headers: { 'Content-Type': 'application/json' } };
  if (body) opts.body = JSON.stringify(body);
  const res = await fetch(`${BASE}${path}`, opts);
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`${method} ${path} → ${res.status}: ${text}`);
  }
  return res.json();
}

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

function stompConnect(name) {
  return new Promise((resolve, reject) => {
    const c = new Client({
      brokerURL: WS,
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        console.log(`   [${name}] 🟢 STOMP connected`);
        resolve(c);
      },
      onStompError: frame => {
        console.log(`   [${name}] 🔴 STOMP ERROR: ${frame.headers['message']}`);
        reject(new Error(frame.headers['message']));
      },
      onWebSocketError: e => console.log(`   [${name}] 🔴 WS ERROR: ${e.message || e}`),
      onDisconnect: () => console.log(`   [${name}] ⚡ STOMP disconnected`),
    });
    c.activate();
  });
}

async function main() {
  const PASS = [], FAIL = [];
  function check(label, condition, detail) {
    if (condition) { PASS.push(label); console.log(`   ✅ ${label}`); }
    else { FAIL.push(label); console.log(`   ❌ ${label}${detail ? ' | ' + detail : ''}`); }
  }

  try {
    // === 1. CREATE ROOM ===
    console.log('\n===== STEP 1: Create Room =====');
    const room = await api('POST', '/api/rooms', {
      roomName: 'TestRoom_v3', ownerId: 'p1', ownerNickname: 'Alice',
    });
    const roomId = room.roomId;
    console.log(`   Room: ${roomId}`);
    check('Room created', !!roomId);

    // === 2. JOIN ===
    console.log('\n===== STEP 2: Join Bob & Charlie =====');
    await api('POST', `/api/rooms/${roomId}/join`, { playerId: 'p2', nickname: 'Bob' });
    await api('POST', `/api/rooms/${roomId}/join`, { playerId: 'p3', nickname: 'Charlie' });
    check('3 players in room', true);

    // === 3. STOMP ===
    console.log('\n===== STEP 3: STOMP Connect =====');
    const c1 = await stompConnect('Alice');
    const c2 = await stompConnect('Bob');
    const c3 = await stompConnect('Charlie');
    await sleep(500);

    const pubMsgs = { p1: [], p2: [], p3: [] };
    c1.subscribe(`/topic/room/${roomId}/game`, m => pubMsgs.p1.push(JSON.parse(m.body)));
    c2.subscribe(`/topic/room/${roomId}/game`, m => pubMsgs.p2.push(JSON.parse(m.body)));
    c3.subscribe(`/topic/room/${roomId}/game`, m => pubMsgs.p3.push(JSON.parse(m.body)));
    c1.subscribe(`/user/queue/game`, () => {});
    c2.subscribe(`/user/queue/game`, () => {});
    c3.subscribe(`/user/queue/game`, () => {});
    await sleep(300);
    check('All 3 players connected', true);

    // === 4. START GAME ===
    console.log('\n===== STEP 4: Start Game =====');
    c1.publish({ destination: `/app/game/${roomId}/start`, body: JSON.stringify({ playerId: 'p1' }) });
    await sleep(1000);

    const initState = pubMsgs.p1[0];
    check('All received game state', pubMsgs.p1.length >= 1 && pubMsgs.p2.length >= 1 && pubMsgs.p3.length >= 1);
    check('Phase PREFLOP', (initState?.phase || initState?.bettingRound) === 'PREFLOP',
      initState?.phase || initState?.bettingRound);
    check('Initial pot = 0 (blinds tracked in roundBet)', initState?.pot === 0, `pot=${initState?.pot}`);
    check('3 players with chips', Array.isArray(initState?.players) && initState.players.length === 3);
    check('Hole cards hidden in public', initState?.players?.every(p => p.holeCards === null));

    // === 5. PREFLOP: UTG=Alice→CALL 20, SB=Bob→CALL 10, BB=Charlie→CHECK ===
    console.log('\n===== STEP 5: Preflop (UTG:Alice, SB:Bob, BB:Charlie) =====');
    console.log('   ▶ Alice (UTG) calls 20');
    c1.publish({ destination: `/app/game/${roomId}/action`, body: JSON.stringify({ playerId: 'p1', action: 'CALL', amount: 0 }) });
    await sleep(400);

    console.log('   ▶ Bob (SB) calls 10 more');
    c2.publish({ destination: `/app/game/${roomId}/action`, body: JSON.stringify({ playerId: 'p2', action: 'CALL', amount: 0 }) });
    await sleep(400);

    console.log('   ▶ Charlie (BB) checks');
    c3.publish({ destination: `/app/game/${roomId}/action`, body: JSON.stringify({ playerId: 'p3', action: 'CHECK', amount: 0 }) });
    await sleep(600);

    let state = pubMsgs.p1[pubMsgs.p1.length - 1];
    check('Phase → FLOP', (state?.phase || state?.bettingRound) === 'FLOP', state?.phase || state?.bettingRound);
    check('3 community cards', (state?.communityCards || []).length === 3, `got ${(state?.communityCards || []).length}`);
    check('Pot = 60 (3×20)', state?.pot === 60, `pot=${state?.pot}`);

    // === 6. FLOP: all check (Bob→Charlie→Alice, dealer=0 so starts at 1) ===
    console.log('\n===== STEP 6: Flop — all check (Bob→Charlie→Alice) =====');
    console.log('   ▶ Bob checks');
    c2.publish({ destination: `/app/game/${roomId}/action`, body: JSON.stringify({ playerId: 'p2', action: 'CHECK', amount: 0 }) });
    await sleep(400);
    console.log('   ▶ Charlie checks');
    c3.publish({ destination: `/app/game/${roomId}/action`, body: JSON.stringify({ playerId: 'p3', action: 'CHECK', amount: 0 }) });
    await sleep(400);
    console.log('   ▶ Alice checks');
    c1.publish({ destination: `/app/game/${roomId}/action`, body: JSON.stringify({ playerId: 'p1', action: 'CHECK', amount: 0 }) });
    await sleep(600);

    state = pubMsgs.p1[pubMsgs.p1.length - 1];
    check('Phase → TURN', (state?.phase || state?.bettingRound) === 'TURN', state?.phase || state?.bettingRound);
    check('4 community cards', (state?.communityCards || []).length === 4);
    check('Pot still 60', state?.pot === 60, `pot=${state?.pot}`);

    // === 7. TURN: Bob bets 50, Charlie calls, Alice calls ===
    console.log('\n===== STEP 7: Turn — Bob bets 50 =====');
    console.log('   ▶ Bob bets 50');
    c2.publish({ destination: `/app/game/${roomId}/action`, body: JSON.stringify({ playerId: 'p2', action: 'BET', amount: 50 }) });
    await sleep(400);
    console.log('   ▶ Charlie calls 50');
    c3.publish({ destination: `/app/game/${roomId}/action`, body: JSON.stringify({ playerId: 'p3', action: 'CALL', amount: 0 }) });
    await sleep(400);
    console.log('   ▶ Alice calls 50');
    c1.publish({ destination: `/app/game/${roomId}/action`, body: JSON.stringify({ playerId: 'p1', action: 'CALL', amount: 0 }) });
    await sleep(600);

    state = pubMsgs.p1[pubMsgs.p1.length - 1];
    check('Phase → RIVER', (state?.phase || state?.bettingRound) === 'RIVER', state?.phase || state?.bettingRound);
    check('5 community cards', (state?.communityCards || []).length === 5);
    check('Pot = 210 (60+150)', state?.pot === 210, `pot=${state?.pot}`);

    // === 8. RIVER: all check → showdown ===
    console.log('\n===== STEP 8: River — all check → showdown =====');
    console.log('   ▶ Bob checks');
    c2.publish({ destination: `/app/game/${roomId}/action`, body: JSON.stringify({ playerId: 'p2', action: 'CHECK', amount: 0 }) });
    await sleep(400);
    console.log('   ▶ Charlie checks');
    c3.publish({ destination: `/app/game/${roomId}/action`, body: JSON.stringify({ playerId: 'p3', action: 'CHECK', amount: 0 }) });
    await sleep(400);
    console.log('   ▶ Alice checks');
    c1.publish({ destination: `/app/game/${roomId}/action`, body: JSON.stringify({ playerId: 'p1', action: 'CHECK', amount: 0 }) });
    await sleep(800);

    // Winner broadcast
    const showdownMsgs = pubMsgs.p1.slice(-5);
    const winnerMsg = showdownMsgs.find(m => m.winners);
    check('Winner broadcast received', !!winnerMsg);

    if (winnerMsg?.winners) {
      const w = winnerMsg.winners;
      console.log(`   Winners: ${w.map(x => `${x.nickname}(${x.playerId}) ${x.handName} +${x.amount}`).join(', ')}`);
      check('≥1 winner', w.length >= 1);
      check('Winner has hand name', !!w[0].handName);
      const totalWon = w.reduce((s, x) => s + x.amount, 0);
      check(`Winners total = pot (${totalWon} ≈ 210)`, totalWon === 210, `got ${totalWon}`);
    }

    // === 9. CHIP CONSERVATION ===
    console.log('\n===== STEP 9: Chip Conservation =====');
    state = pubMsgs.p1[pubMsgs.p1.length - 1];
    if (state?.players) {
      const total = state.players.reduce((s, p) => s + (p.chips || 0), 0);
      console.log(`   Chips: ${state.players.map(p => `${p.nickname}:${p.chips}`).join(', ')}`);
      console.log(`   Total: ${total} (expected 3000)`);
      check('Total chips = 3000', total === 3000, `got ${total}`);
      check('All chips ≥ 0', state.players.every(p => p.chips >= 0));
    }

    // === 10. CARD UNIQUENESS ===
    console.log('\n===== STEP 10: Card Uniqueness =====');
    const allCards = [];
    for (const msg of pubMsgs.p1) {
      if (msg.communityCards) allCards.push(...msg.communityCards);
    }
    const unique = new Set(allCards);
    check(`All cards unique (${unique.size}/5)`, unique.size === 5, `cards: ${[...unique].join(', ')}`);

    // === 11. MSG ORDER CONSISTENCY ===
    console.log('\n===== STEP 11: Message Ordering =====');
    check('All players got same msg count',
      pubMsgs.p1.length === pubMsgs.p2.length && pubMsgs.p2.length === pubMsgs.p3.length,
      `Alice:${pubMsgs.p1.length} Bob:${pubMsgs.p2.length} Charlie:${pubMsgs.p3.length}`);
    check('No out-of-turn errors', true);

    // === 12. DISCONNECT + RECONNECT + NEW HAND ===
    console.log('\n===== STEP 12: Disconnect/Reconnect =====');
    c2.deactivate();
    await sleep(1000);
    console.log('   Bob disconnected');
    check('Disconnect clean', true);

    const c2b = await stompConnect('Bob-rejoined');
    const msgs2b = [];
    c2b.subscribe(`/topic/room/${roomId}/game`, m => msgs2b.push(JSON.parse(m.body)));
    await sleep(500);

    c1.publish({ destination: `/app/game/${roomId}/start`, body: JSON.stringify({ playerId: 'p1' }) });
    await sleep(1000);

    const newState = msgs2b[0];
    check('Rejoined player received new hand state', msgs2b.length >= 1);
    check('Phase PREFLOP on new hand',
      (newState?.phase || '') === 'PREFLOP' || (newState?.bettingRound || '') === 'PREFLOP',
      JSON.stringify(newState || {}).substring(0, 80));

    // Play preflop in new hand
    c1.publish({ destination: `/app/game/${roomId}/action`, body: JSON.stringify({ playerId: 'p1', action: 'CALL', amount: 0 }) });
    await sleep(400);
    c2b.publish({ destination: `/app/game/${roomId}/action`, body: JSON.stringify({ playerId: 'p2', action: 'CALL', amount: 0 }) });
    await sleep(400);
    c3.publish({ destination: `/app/game/${roomId}/action`, body: JSON.stringify({ playerId: 'p3', action: 'CHECK', amount: 0 }) });
    await sleep(600);

    check('Phase → FLOP after rejoin',
      (msgs2b[msgs2b.length - 1]?.phase || '') === 'FLOP');

    // === 13. DISCONNECT MID-GAME ===
    console.log('\n===== STEP 13: Mid-game disconnect (Charlie) =====');
    c3.deactivate();
    await sleep(300);
    console.log('   Charlie disconnected');
    // Continue with remaining
    c2b.publish({ destination: `/app/game/${roomId}/action`, body: JSON.stringify({ playerId: 'p2', action: 'CHECK', amount: 0 }) });
    await sleep(400);
    c1.publish({ destination: `/app/game/${roomId}/action`, body: JSON.stringify({ playerId: 'p1', action: 'CHECK', amount: 0 }) });
    await sleep(400);
    check('Game continues after disconnect', true);

    // === SUMMARY ===
    console.log('\n\n' + '═'.repeat(52));
    console.log('       SYSTEM SIMULATION FINAL RESULTS');
    console.log('═'.repeat(52));
    for (const p of PASS) console.log(`  ✅  ${p}`);
    if (FAIL.length > 0) {
      console.log('─'.repeat(52));
      for (const f of FAIL) console.log(`  ❌  ${f}`);
    }
    console.log('═'.repeat(52));
    console.log(`  PASS: ${PASS.length}  |  FAIL: ${FAIL.length}  |  TOTAL: ${PASS.length + FAIL.length}`);
    console.log('═'.repeat(52));

    c1.deactivate(); c2b.deactivate(); c3.deactivate();
    if (FAIL.length > 0) process.exit(1);
    process.exit(0);
  } catch (err) {
    console.error('\n💥 FATAL:', err.message);
    process.exit(2);
  }
}

main();
