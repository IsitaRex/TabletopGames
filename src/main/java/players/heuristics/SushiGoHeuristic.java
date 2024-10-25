package players.heuristics;

import core.AbstractGameState;
import core.interfaces.IStateHeuristic;

public class SushiGoHeuristic implements IStateHeuristic {
    @Override
    public double evaluateState(AbstractGameState gs, int playerId){
        /* Returns the difference between players score and the player with the highest score */
        /* First, find the player with the highest score */
        double maxScore = 0;
        for (int i = 0; i < gs.getNPlayers(); i++) {
            double score = gs.getGameScore(i);
            maxScore = Math.max(score, maxScore);
        }
        /* Then, return the difference between the player's score and the max score */
        return gs.getGameScore(playerId) - maxScore;
    }

}
