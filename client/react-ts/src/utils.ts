type Color = string;

export interface UniquelyIdentified {
    uniqueId: string;
}
export interface Named {
    name: string;
}

export class Vec2 {
    public readonly x: number;
    public readonly y: number;
    public static readonly ZERO = new Vec2(0, 0);
    constructor(x: number, y: number) {
        this.x = x;
        this.y = y;
    }
}

export abstract class Entity implements UniquelyIdentified {
    uniqueId!: string;
    readonly position!: Vec2;
    readonly velocity?: Vec2 = Vec2.ZERO;
    readonly radius!: number;
}
export class Food extends Entity {
    readonly color!: Color;
}
export class Cell extends Entity {
    declare position: Vec2;
    declare velocity: Vec2;
    declare radius: number;
}
export class Player implements UniquelyIdentified, Named {
    uniqueId!: string;
    name!: string;
    color!: Color;
    cells!: Cell[];
    targetPosition!: Vec2;
}

export interface Camera {
    position: Vec2;
    zoom: number;
}

export class GameState {
    players!: Map<string, Player>;
    food!: Map<string, Food>;
    //timestamp!: number;
}
