import {useState} from "react";

interface Props {
    play: (username: string) => void;
}

const USERNAME_MAX_LENGTH = 16;

export default function MainMenu(props: Props) {
    const [username, setUsername] = useState('');
    return (<>
        <div>
            <h1>Aphelion</h1>
            <input type="text" placeholder="Enter username..." maxLength={USERNAME_MAX_LENGTH} onChange={(event) => setUsername(event.target.value)} defaultValue={username} />
            <button onClick={() => props.play(username)}>PLAY</button>
        </div>
    </>)
}
