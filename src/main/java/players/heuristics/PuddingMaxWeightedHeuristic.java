package players.heuristics;

import core.AbstractGameState;
import core.interfaces.IStateHeuristic;
import games.sushigo.cards.SGCard;
import games.sushigo.SGGameState;

public class PuddingMaxWeightedHeuristic implements IStateHeuristic {
    @Override
    public double evaluateState(AbstractGameState gs, int playerId){
        /* Returns the score of the player and adds 6 points if the player has the most pudding*/
        int maxPudding =  0;
        int minPudding = 100;
        int puddingsLeft = 10;
        double maxScore = 0;
        SGGameState sgState = (SGGameState) gs;
        for (int i = 0; i < gs.getNPlayers(); i++) {
            int nPuddings = sgState.getPlayedCardTypes()[i].get(SGCard.SGCardType.Pudding).getValue();
            double score = gs.getGameScore(i);
            maxScore = Math.max(score, maxScore);
            maxPudding = Math.max(nPuddings, maxPudding);
            minPudding = Math.min(nPuddings, minPudding);
            puddingsLeft -= nPuddings;
        }
        double weight = 1.0 - (puddingsLeft / 10.0);
        int playerPudding = sgState.getPlayedCardTypes()[playerId].get(SGCard.SGCardType.Pudding).getValue();
        if (playerPudding == maxPudding) {
            return gs.getGameScore(playerId) + weight*6 - maxScore;
        }
        if(playerPudding == minPudding){
            return gs.getGameScore(playerId) - weight*6 - maxScore;
        }
        return gs.getGameScore(playerId) - maxScore;
    }

}
