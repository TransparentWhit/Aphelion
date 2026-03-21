import type {GameState, Vec2} from "./utils.ts";

abstract class ServerBoundPacket {
    abstract packetId: string;
    timestamp: number = Date.now();
}
class LoginStartPacket extends ServerBoundPacket {
    static PACKET_ID = 'LOGIN_START';
    override packetId = LoginStartPacket.PACKET_ID;
    username: string;
    constructor(username: string) {
        super();
        this.username = username;
    }
}
class MouseUpdatePacket extends ServerBoundPacket {
    static PACKET_ID = 'MOUSE_UPDATE';
    override packetId = MouseUpdatePacket.PACKET_ID;
    position: Vec2;
    constructor(position: Vec2) {
        super();
        this.position = position;
    }
}
class SplitPacket extends ServerBoundPacket {
    static PACKET_ID = 'SPLIT';
    override packetId = SplitPacket.PACKET_ID;
}

export abstract class ClientBoundPacket {
    abstract packetId: string;
}
export class LoginSuccessPacket extends ClientBoundPacket {
    static PACKET_ID = 'LOGIN_SUCCESS';
    override packetId = LoginSuccessPacket.PACKET_ID;
    playerId!: string;
}
export class GameStatePacket extends ClientBoundPacket {
    static PACKET_ID = 'GAME_STATE';
    override packetId = GameStatePacket.PACKET_ID;
    gameState!: GameState;
}

export class NetworkManager {
    private readonly url: string;
    private readonly packetHandler: (packet: ClientBoundPacket) => void;
    private readonly disconnectedHandler: () => void;
    private websocket: WebSocket | null = null;
    private reconnectAttemptTimeout: number | null = null;
    private disconnectedFlag: boolean = false;
    constructor(url: string, packetHandler: (packet: ClientBoundPacket) => void, disconnectedHandler: () => void) {
        this.url = url;
        this.packetHandler = packetHandler;
        this.disconnectedHandler = disconnectedHandler;
    }
    public connect() {
        console.log("flag_call_connect");
        return new Promise<void>((resolve, reject) => {
            try {
                if (this.isConnected()) {
                    resolve();
                    return;
                }
                this.disconnectedFlag = false;
                this.websocket = new WebSocket(this.url);
                this.websocket.onopen = () => {
                    console.log('Connected to server (%s)', this.url);
                    this.reconnectDelay = NetworkManager.MIN_RECONNECT_DELAY;
                    resolve();
                };
                this.websocket.onmessage = (event) => {
                    this.handleMessage(event);
                };
                this.websocket.onclose = () => {
                    console.log('Disconnected from server (%s)', this.url);
                    this.attemptReconnect();
                };
                this.websocket.onerror = (error) => {
                    console.error('WebSocket error: %s', error);
                    reject(error);
                };
            } catch (error) {
                reject(error);
            }
        });
    }
    private isConnected() {
        return this.websocket && this.websocket.readyState == WebSocket.OPEN;
    }
    private send(packet: ServerBoundPacket) {
        if (this.isConnected()) {
            // @ts-expect-error - this#isConnected has checked for null
            this.websocket.send(JSON.stringify(packet));
        } else {
            console.warn('WebSocket not connected');
        }
    }
    public login(username: string) {
        this.send(new LoginStartPacket(username));
    }
    public updateMouse(position: Vec2) {
        this.send(new MouseUpdatePacket(position));
    }
    public split() {
        this.send(new SplitPacket());
    }
    private handleMessage(event: MessageEvent) {
        if (this.packetHandler) {
            try {
                this.packetHandler(JSON.parse(event.data));
            } catch (error) {
                console.error('Error parsing packet: %s', error);
            }
        }
    }
    public disconnect() {
        console.log("flag_call_disconnect");
        this.disconnectedFlag = true;
        if (this.websocket) {
            this.websocket.close();
            this.websocket = null;
        }
        if (this.reconnectAttemptTimeout) {
            clearTimeout(this.reconnectAttemptTimeout);
            this.reconnectAttemptTimeout = null;
        }
    }
    private static MIN_RECONNECT_DELAY = 128;
    private static MAX_RECONNECT_DELAY = 10000;
    private static RECONNECT_DELAY_MULTIPLIER = 2;
    private static RECONNECT_DELAY_JITTER = 128;
    private reconnectDelay = NetworkManager.MIN_RECONNECT_DELAY;
    private attemptReconnect() {
        if (this.disconnectedFlag) return;
        if (this.reconnectDelay > NetworkManager.MAX_RECONNECT_DELAY) {
            this.disconnectedHandler();
            return;
        }
        this.reconnectDelay = this.reconnectDelay * NetworkManager.RECONNECT_DELAY_MULTIPLIER + Math.random() * NetworkManager.RECONNECT_DELAY_JITTER;
        console.log('Attempting to reconnect...', this.reconnectDelay);
        this.reconnectAttemptTimeout = setTimeout(() => {
            this.connect().catch(error => {
                console.warn('Reconnection failed: %s', error);
                this.attemptReconnect();
            });
        }, this.reconnectDelay);
    }
}
