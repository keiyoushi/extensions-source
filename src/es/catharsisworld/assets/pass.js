// Keeps the site's reading pass active: the site grants it in the browser alone once its rewarded ad plays.
() => {
    const key = 'catharsis_reward_gate';
    const read = () => {
        try {
            return JSON.parse(localStorage.getItem(key)) ?? {};
        } catch {
            return {};
        }
    };
    const state = read();
    if (!(state.passUntil > Date.now())) localStorage.setItem(key, JSON.stringify({ ...state, passUntil: Date.now() + 45 * 60 * 1000 }));
}
