/**
 * Poker Bot Arena — 加入房间等待游戏开始
 * 用法: node bots.mjs <roomId> <botIds> [wsHost]
 */
import { Client } from '@stomp/stompjs';
import WebSocket from 'ws';

Object.assign(globalThis, { WebSocket });

const ROOM_ID = process.argv[2];
const BOT_IDS_STR = process.argv[3] || '';
const WS_HOST = process.argv[4] || 'localhost:8081';
const WS = `ws://${WS_HOST}/ws-native`;

const BOT_IDS = BOT_IDS_STR.split(',').map(s => s.trim()).filter(Boolean);
const processedTurns = new Map();

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

function onGameState(client, botId, state) {
  if (!state?.players) return;
  const me = state.players.find(p => p.playerId === botId);
  if (!me || me.folded || me.allIn) return;

  const phase = (state.phase || state.bettingRound || '');
  if (phase === 'SHOWDOWN' || phase === 'HAND_OVER') return;

  // Winners
  if (state?.winners?.length > 0) {
    const handId = `${state.winners.map(w => w.playerId).join(',')}`;
    if (processedTurns.get('__winner') !== handId) {
      processedTurns.set('__winner', handId);
      console.log(`\n🏆 ${state.winners.map(w => `${w.nickname} (${w.handName}) +${w.amount}`).join(', ')}\n`);
    }
    return;
  }

  const currentIdx = state.currentPlayerIndex ?? -1;
  if (currentIdx < 0) return;
  const myIdx = state.players.findIndex(p => p.playerId === botId);
  if (myIdx !== currentIdx) return;

  const turnKey = `${phase}-${currentIdx}-${state.pot}`;
  if (processedTurns.get(botId) === turnKey) return;
  processedTurns.set(botId, turnKey);

  const toCall = (state.currentBet || 0) - (me.betInRound || 0);
  const action = toCall <= 0 ? 'CHECK' : 'CALL';

  setTimeout(() => {
    console.log(`  [${me.nickname || botId}] ▶ ${action} | pot=${state.pot} ${phase}`);
    client.publish({
      destination: `/app/game/${ROOM_ID}/action`,
      body: JSON.stringify({ playerId: botId, action, amount: 0 }),
    });
  }, 400 + Math.random() * 600);
}

async function main() {
  console.log(`\n🤖 连接 ${BOT_IDS.length} 个机器人 → 房间 ${ROOM_ID}\n`);

  for (const botId of BOT_IDS) {
    const client = new Client({
      brokerURL: WS,
      reconnectDelay: 3000,
      onConnect: () => {
        console.log(`  [${botId}] 🟢 上线`);
        client.subscribe(`/topic/room/${ROOM_ID}/game`, msg => {
          onGameState(client, botId, JSON.parse(msg.body));
        });
      },
      onDisconnect: () => {},
    });
    client.activate();
    await sleep(200);
  }

  console.log(`\n🤖 就绪！打开 http://localhost:5173 加入房间 ${ROOM_ID} 然后开始游戏\n`);
}

main().catch(e => { console.error('💥', e.message); process.exit(2); });
