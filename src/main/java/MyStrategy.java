import model.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
public final class MyStrategy implements Strategy {
	
	private MyStrategyHelper myStrategyHelper = new MyStrategyHelper();
    public Random random;

    private TerrainType[][] terrainTypeByCellXY;
    private WeatherType[][] weatherTypeByCellXY;

    /**
     * Main strategy method, controlling the vehicles.
     * The game engine calls this method once each time tick.
     *
     * @param me    the owner player of this strategy.
     * @param world the current world snapshot.
     * @param game  many game constants.
     * @param move  the object that encapsulates all strategy instructions.
     */
    @Override
    public void move(Player me, World world, Game game, Move move) {
        initializeStrategy(world, game);
        myStrategyHelper.initializeTick(me, world, game, move);

        if (me.getRemainingActionCooldownTicks() > 0) {
            return;
        }

        if (myStrategyHelper.executeDelayedMove()) {
            return;
        }

        myStrategyHelper.move(new MyStrategy());

        myStrategyHelper.executeDelayedMove();
    }
    
    /**
     * Initialize our strategy.
     * <p>
     * Usually you can use a constructor, but in this case we want to initialize the generator of random numbers
     * with a value obtained from the game engine.
     */
    private void initializeStrategy(World world, Game game) {
        if (random == null) {
            random = new Random(game.getRandomSeed());

            terrainTypeByCellXY = world.getTerrainByCellXY();
            weatherTypeByCellXY = world.getWeatherByCellXY();
        }
    }


}
