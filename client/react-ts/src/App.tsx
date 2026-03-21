import {useState} from 'react'
import './App.css'
import MainMenu from "./MainMenu.tsx";
import Game from "./Game.tsx";

const AppViews = {
    MAIN_MENU: 'main_menu',
    GAME: 'game',
} as const;

export default function App() {
    const [view, setView] = useState<string>(AppViews.MAIN_MENU);
    const [username, setUsername] = useState('Player');
    return (<>
        {view === AppViews.MAIN_MENU &&
            <MainMenu play={(_username: string) => {
                setUsername(_username);
                setView(AppViews.GAME);
            }} />
        }
        {view === AppViews.GAME &&
            <Game username={username} quit={() => {
                setView(AppViews.MAIN_MENU);
            }} />
        }
    </>);
}
