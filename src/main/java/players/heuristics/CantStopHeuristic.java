package players.heuristics;

import core.AbstractGameState;
import core.interfaces.IStateHeuristic;

public class CantStopHeuristic implements IStateHeuristic {
    @Override
    public double evaluateState(AbstractGameState gs, int playerId){
        return 0;
    }
}
