// The site's series page runs the challenge the same way (series.js, solveAndGetToken)
async (challengeJs) => {
    const answer = await new Function(challengeJs)();
    return String(answer);
}
