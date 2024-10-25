package players.heuristics;

import core.AbstractGameState;
import core.interfaces.IStateHeuristic;
import games.sushigo.cards.SGCard;
import games.sushigo.SGGameState;

public class PuddingHeuristic implements IStateHeuristic {
    @Override
    public double evaluateState(AbstractGameState gs, int playerId){
        /* Returns the score of the player and adds 6 points if the player has the most pudding*/
        int maxPudding =  0;
        int minPudding = 100;
        SGGameState sgState = (SGGameState) gs;
        for (int i = 0; i < gs.getNPlayers(); i++) {
            int nPuddings = sgState.getPlayedCardTypes()[i].get(SGCard.SGCardType.Pudding).getValue();
            maxPudding = Math.max(nPuddings, maxPudding);
            minPudding = Math.min(nPuddings, minPudding);
        }
        int playerPudding = sgState.getPlayedCardTypes()[playerId].get(SGCard.SGCardType.Pudding).getValue();
        if (playerPudding == maxPudding) {
            return gs.getGameScore(playerId) + 6;
        }
        if(playerPudding == minPudding){
            return gs.getGameScore(playerId) - 6;
        }
        return gs.getGameScore(playerId);
    }

}
