import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Stream;

import model.ActionType;
import model.Game;
import model.Move;
import model.Player;
import model.TerrainType;
import model.Vehicle;
import model.VehicleType;
import model.VehicleUpdate;
import model.WeatherType;
import model.World;

/**
 * 
 */

/**
 * @author kkanaparthi
 *
 */
public class MyStrategyHelper {

    /**
     * A map from vehicle type to target types ordered by effective damage descending.
     */
    private static final Map<VehicleType, VehicleType[]> preferredTargetTypesByVehicleType;

    static {
        preferredTargetTypesByVehicleType = new EnumMap<>(VehicleType.class);

        preferredTargetTypesByVehicleType.put(VehicleType.FIGHTER, new VehicleType[] {
                VehicleType.HELICOPTER, VehicleType.FIGHTER
        });

        preferredTargetTypesByVehicleType.put(VehicleType.HELICOPTER, new VehicleType[] {
                VehicleType.TANK, VehicleType.ARRV, VehicleType.HELICOPTER, VehicleType.IFV, VehicleType.FIGHTER
        });

        preferredTargetTypesByVehicleType.put(VehicleType.IFV, new VehicleType[] {
                VehicleType.HELICOPTER, VehicleType.ARRV, VehicleType.IFV, VehicleType.FIGHTER, VehicleType.TANK
        });

        preferredTargetTypesByVehicleType.put(VehicleType.TANK, new VehicleType[] {
                VehicleType.IFV, VehicleType.ARRV, VehicleType.TANK, VehicleType.FIGHTER, VehicleType.HELICOPTER
        });
    }


    private Player me;
    private World world;
    private Game game;
    private Move move;

    private final Map<Long, Vehicle> vehicleById = new HashMap<>();
    private final Map<Long, Integer> updateTickByVehicleId = new HashMap<>();
    private final Queue<Consumer<Move>> delayedMoves = new ArrayDeque<>();





    /**
     * Save all input data in the strategy fields for simpler access and actualize vehicle data.
     */
    public void initializeTick(Player me, World world, Game game, Move move) {
        this.me = me;
        this.world = world;
        this.game = game;
        this.move = move;

        for (Vehicle vehicle : world.getNewVehicles()) {
            vehicleById.put(vehicle.getId(), vehicle);
            updateTickByVehicleId.put(vehicle.getId(), world.getTickIndex());
        }

        for (VehicleUpdate vehicleUpdate : world.getVehicleUpdates()) {
            long vehicleId = vehicleUpdate.getId();

            if (vehicleUpdate.getDurability() == 0) {
                vehicleById.remove(vehicleId);
                updateTickByVehicleId.remove(vehicleId);
            } else {
                vehicleById.put(vehicleId, new Vehicle(vehicleById.get(vehicleId), vehicleUpdate));
                updateTickByVehicleId.put(vehicleId, world.getTickIndex());
            }
        }
    }

    /**
     * Take delayed move from queue and execute.
     *
     * @return {@code true} if and only if a delayed move has been found and executed.
     */
    public boolean executeDelayedMove() {
        Consumer<Move> delayedMove = delayedMoves.poll();
        if (delayedMove == null) {
            return false;
        }

        delayedMove.accept(move);
        return true;
    }

    /**
     * The core logic of our strategy.
     */
    public void move(MyStrategy myStrategy) {
        // Every 180 ticks ...
        if (world.getTickIndex() % 180 == 0) {
            // ... for each vehicle type ...
            for (VehicleType vehicleType : VehicleType.values()) {
                VehicleType[] targetTypes = preferredTargetTypesByVehicleType.get(vehicleType);

                // ... if it can attack ...
                if (targetTypes == null || targetTypes.length == 0) {
                    continue;
                }

                // ... find center of our formation ...
                double x = streamVehicles(
                        Ownership.ALLY, vehicleType
                ).mapToDouble(Vehicle::getX).average().orElse(Double.NaN);

                double y = streamVehicles(
                        Ownership.ALLY, vehicleType
                ).mapToDouble(Vehicle::getY).average().orElse(Double.NaN);

                // ... find center of enemy formation or center of the world ...
                double targetX = Arrays.stream(targetTypes).map(
                        targetType -> streamVehicles(
                                Ownership.ENEMY, targetType
                        ).mapToDouble(Vehicle::getX).average().orElse(Double.NaN)
                ).filter(Double::isFinite).findFirst().orElseGet(
                        () -> streamVehicles(
                                Ownership.ENEMY
                        ).mapToDouble(Vehicle::getX).average().orElse(world.getWidth() / 2.0D)
                );

                double targetY = Arrays.stream(targetTypes).map(
                        targetType -> streamVehicles(
                                Ownership.ENEMY, targetType
                        ).mapToDouble(Vehicle::getY).average().orElse(Double.NaN)
                ).filter(Double::isFinite).findFirst().orElseGet(
                        () -> streamVehicles(
                                Ownership.ENEMY
                        ).mapToDouble(Vehicle::getY).average().orElse(world.getHeight() / 2.0D)
                );

                // .. and add delayed moves to select and move our vehicles.
                if (!Double.isNaN(x) && !Double.isNaN(y)) {
                    delayedMoves.add(move -> {
                        move.setAction(ActionType.CLEAR_AND_SELECT);
                        move.setRight(world.getWidth());
                        move.setBottom(world.getHeight());
                        move.setVehicleType(vehicleType);
                    });

                    delayedMoves.add(move -> {
                        move.setAction(ActionType.MOVE);
                        move.setX(targetX - x);
                        move.setY(targetY - y);
                    });
                }
            }

            // Also find center of our ARRV formation ...
            double x = streamVehicles(
                    Ownership.ALLY, VehicleType.ARRV
            ).mapToDouble(Vehicle::getX).average().orElse(Double.NaN);

            double y = streamVehicles(
                    Ownership.ALLY, VehicleType.ARRV
            ).mapToDouble(Vehicle::getY).average().orElse(Double.NaN);

            // .. and send it to the center of the world.
            if (!Double.isNaN(x) && !Double.isNaN(y)) {
                delayedMoves.add(move -> {
                    move.setAction(ActionType.CLEAR_AND_SELECT);
                    move.setRight(world.getWidth());
                    move.setBottom(world.getHeight());
                    move.setVehicleType(VehicleType.ARRV);
                });

                delayedMoves.add(move -> {
                    move.setAction(ActionType.MOVE);
                    move.setX(world.getWidth() / 2.0D - x);
                    move.setY(world.getHeight() / 2.0D - y);
                });
            }

            return;
        }

        // If all our vehicles are stuck for 60 ticks ...
        if (streamVehicles(Ownership.ALLY).allMatch(
                vehicle -> world.getTickIndex() - updateTickByVehicleId.get(vehicle.getId()) > 60
        )) {
            // ... find center of our formation ...
            double x = streamVehicles(Ownership.ALLY).mapToDouble(Vehicle::getX).average().orElse(Double.NaN);
            double y = streamVehicles(Ownership.ALLY).mapToDouble(Vehicle::getY).average().orElse(Double.NaN);

            // ... and rotate it.
            if (!Double.isNaN(x) && !Double.isNaN(y)) {
                delayedMoves.add(move -> {
                    move.setAction(ActionType.CLEAR_AND_SELECT);
                    move.setRight(world.getWidth());
                    move.setBottom(world.getHeight());
                });

                delayedMoves.add(move -> {
                    move.setAction(ActionType.ROTATE);
                    move.setX(x);
                    move.setY(y);
                    move.setAngle(myStrategy.random.nextBoolean() ? StrictMath.PI : -StrictMath.PI);
                });
            }
        }
    }

    private Stream<Vehicle> streamVehicles(Ownership ownership, VehicleType vehicleType) {
        Stream<Vehicle> stream = vehicleById.values().stream();

        switch (ownership) {
            case ALLY:
                stream = stream.filter(vehicle -> vehicle.getPlayerId() == me.getId());
                break;
            case ENEMY:
                stream = stream.filter(vehicle -> vehicle.getPlayerId() != me.getId());
                break;
            default:
        }

        if (vehicleType != null) {
            stream = stream.filter(vehicle -> vehicle.getType() == vehicleType);
        }

        return stream;
    }

    private Stream<Vehicle> streamVehicles(Ownership ownership) {
        return streamVehicles(ownership, null);
    }

    private Stream<Vehicle> streamVehicles() {
        return streamVehicles(Ownership.ANY);
    }

    private enum Ownership {
        ANY,

        ALLY,

        ENEMY
    }

}
