import {useEffect, useRef, useState} from "react";
import {type GameState, Vec2} from "./utils.ts";
import {GameStatePacket, LoginSuccessPacket, NetworkManager} from "./network.ts";

interface Props {
    readonly username: string;
    readonly quit: () => void;
}

const TWO_PI = 2 * Math.PI;

//const BASE_ZOOM = 1;

const WIDTH = 1920;
const HEIGHT = 1080;

export default function Game(props: Props) {
    const playerIdRef = useRef<string | null>(null);
    const canvasRef = useRef<HTMLCanvasElement>(null);
    const [gameState, setGameState] = useState<GameState | null>(null);
    //const [camera, setCamera] = useState<Camera>({ position: { x: 0, y: 0 }, zoom: BASE_ZOOM });
    const networkManagerRef = useRef(new NetworkManager('ws://localhost:8080/game', packet => {
        switch (packet.packetId) {
            case LoginSuccessPacket.PACKET_ID:
                playerIdRef.current = (packet as LoginSuccessPacket).playerId;
                console.log("Login success: ", playerIdRef.current);
                break;
            case GameStatePacket.PACKET_ID:
                setGameState((packet as GameStatePacket).gameState);
                break;
        }
    }, props.quit));
    const networkManager = networkManagerRef.current;
    const propsRef = useRef(props);
    useEffect(() => {
        const onPointerMove = (event: PointerEvent) => {
            const canvas = canvasRef.current;
            if (!canvas) return;
            networkManager.updateMouse(new Vec2((event.x - canvas.offsetLeft) * WIDTH / canvas.width, (event.y - canvas.offsetTop) * HEIGHT / canvas.height));
        };
        const onKeyDown = (event: KeyboardEvent) => {
            if (event.key === ' ') {
                event.preventDefault();
                networkManager.split();
            }
        };
        window.addEventListener('pointermove', onPointerMove);
        window.addEventListener('keydown', onKeyDown);
        return () => {
            window.removeEventListener('pointermove', onPointerMove);
            window.removeEventListener('keydown', onKeyDown);
        };
    });
    useEffect(() => {
        networkManager.connect().then(() => networkManager.login(propsRef.current.username)).catch(propsRef.current.quit);
        return () => networkManager.disconnect();
    }, [networkManager]);
    const paintRef = useRef<number>(0);
    const prevTimeRef = useRef<number>(0);
    const resizeCanvas = () => {
        const canvas = canvasRef.current;
        if (!canvas) return;
        const ctx = canvas.getContext('2d');
        if (!ctx) return;
        const dpr = window.devicePixelRatio || 1;
        canvas.width = canvas.clientWidth * dpr;
        canvas.height = canvas.clientHeight * dpr;
    };
    useEffect(() => {
        const canvas = canvasRef.current;
        if (!canvas) return;
        const ctx = canvas.getContext('2d');
        if (!ctx) return;
        const paint = (currentTime: number) => {
            //const deltaTime = prevTimeRef.current ? currentTime - prevTimeRef.current : 0;
            prevTimeRef.current = currentTime;
            ctx.save();
            ctx.fillStyle = '#070707';
            const width = canvas.width, height = canvas.height;
            ctx.fillRect(0, 0, width, height);
            ctx.scale(canvas.width / WIDTH, canvas.height / HEIGHT);
            if (playerIdRef && gameState) {
                gameState.players.forEach(player => {
                    player.cells.forEach(cell => {
                        ctx.beginPath();
                        ctx.arc(cell.position.x, cell.position.y, cell.radius, 0, TWO_PI);
                        ctx.fillStyle = player.color;
                        ctx.fill();
                    });
                });
            } else {
                ctx.fillStyle = '#bbe7ff';
                ctx.fillText("Loading...", 960, 540);
            }
            ctx.restore();
            paintRef.current = requestAnimationFrame(paint);
        };
        resizeCanvas();
        window.addEventListener('resize', resizeCanvas);
        paintRef.current = requestAnimationFrame(paint);
        return () => {
            window.removeEventListener('resize', resizeCanvas);
            cancelAnimationFrame(paintRef.current);
        };
    });
    return (<><div style={{
        width: '100%',
        height: '100%',
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        backgroundColor: 'black'
    }}><div style={{
        width: '100%',
        height: '100%',
        maxWidth: 'calc(100vh * 16 / 9)',
        maxHeight: 'calc(100vw * 9 / 16)',
        aspectRatio: '16/9'
    }}>
        <canvas ref={canvasRef} style={{
            width: '100%',
            height: '100%'
        }} />
    </div></div></>)
}
