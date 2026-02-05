package org.fog.test.perfeval;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.entities.Actuator;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementMapping;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.entities.FogDeviceCharacteristics;

public class PrecisionAgricultureFog {
    static List<FogDevice> fogDevices = new ArrayList<>();
    static List<Sensor> sensors = new ArrayList<>();
    static List<Actuator> actuators = new ArrayList<>();
    static int numOfZones = 4; // Number of zones on the farm
    private static boolean CLOUD = true;

    public static void main(String[] args) {
        try {
            Log.disable();
            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            CloudSim.init(num_user, calendar, false);

            String appId = "farming";
            FogBroker broker = new FogBroker("broker");

            Application application = createApplication(appId, broker.getId());

            createFogDevices(broker.getId(), appId);

            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
            for (FogDevice device : fogDevices) {
                if (device.getName().startsWith("edge")) {
                    moduleMapping.addModuleToDevice("soil_moisture_module", device.getName());
                    moduleMapping.addModuleToDevice("temperature_module", device.getName());
                    moduleMapping.addModuleToDevice("humidity_module", device.getName());
                    moduleMapping.addModuleToDevice("pH_module", device.getName());
                    moduleMapping.addModuleToDevice("light_intensity_module", device.getName());
                }
            }

            if (CLOUD) {
                moduleMapping.addModuleToDevice("soil_moisture_module", "cloud");
                moduleMapping.addModuleToDevice("temperature_module", "cloud");
                moduleMapping.addModuleToDevice("humidity_module", "cloud");
                moduleMapping.addModuleToDevice("pH_module", "cloud");
                moduleMapping.addModuleToDevice("light_intensity_module", "cloud");
                moduleMapping.addModuleToDevice("data_analyzer", "cloud");
            }
            moduleMapping.addModuleToDevice("data_analyzer", "cloud");
            Controller controller = new Controller("master-controller", fogDevices, sensors, actuators);
            controller.submitApplication(application,
                    (CLOUD) ? (new ModulePlacementMapping(fogDevices, application, moduleMapping))
                            : (new ModulePlacementEdgewards(fogDevices, sensors, actuators, application, moduleMapping)));

            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            Log.printLine("Farming Simulation finished!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happened");
        }
    }

    private static void createFogDevices(int userId, String appId) throws Exception {
        FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01, 16 * 103, 16 * 83.25);
        cloud.setParentId(-1);
        fogDevices.add(cloud);

        FogDevice proxy = createFogDevice("proxy-server", 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333);
        proxy.setParentId(cloud.getId());
        proxy.setUplinkLatency(100);
        fogDevices.add(proxy);

        // Create fog devices for each zone
        for (int i = 0; i < numOfZones; i++) {
            FogDevice edgeDevice = createFogDevice("edge-zone-" + (i + 1), 2800, 4000, 1000, 10000, 2, 0.0, 107.339, 83.4333);
            edgeDevice.setParentId(proxy.getId());
            edgeDevice.setUplinkLatency(2);
            fogDevices.add(edgeDevice);
            linkSensorsAndActuatorsToFogDevices(userId, appId, edgeDevice);
        }
    }

    private static void linkSensorsAndActuatorsToFogDevices(int userId, String appId, FogDevice edgeDevice) {
        // Create sensors and actuators for each zone
        Sensor soilMoistureSensor = new Sensor("soilMoistureSensor-" + edgeDevice.getName(), "SOIL_MOISTURE", userId, appId, new DeterministicDistribution(5));
        soilMoistureSensor.setGatewayDeviceId(edgeDevice.getId());
        sensors.add(soilMoistureSensor);

        Sensor temperatureSensor = new Sensor("temperatureSensor-" + edgeDevice.getName(), "TEMPERATURE", userId, appId, new DeterministicDistribution(6));
        temperatureSensor.setGatewayDeviceId(edgeDevice.getId());
        sensors.add(temperatureSensor);

        Sensor humiditySensor = new Sensor("humiditySensor-" + edgeDevice.getName(), "HUMIDITY", userId, appId, new DeterministicDistribution(7));
        humiditySensor.setGatewayDeviceId(edgeDevice.getId());
        sensors.add(humiditySensor);

        Sensor pHSensor = new Sensor("pHSensor-" + edgeDevice.getName(), "PH", userId, appId, new DeterministicDistribution(4));
        pHSensor.setGatewayDeviceId(edgeDevice.getId());
        sensors.add(pHSensor);

        Sensor lightIntensitySensor = new Sensor("lightIntensitySensor-" + edgeDevice.getName(), "LIGHT_INTENSITY", userId, appId, new DeterministicDistribution(5));
        lightIntensitySensor.setGatewayDeviceId(edgeDevice.getId());
        sensors.add(lightIntensitySensor);

        Actuator irrigationActuator = new Actuator("irrigationActuator-" + edgeDevice.getName(), userId, appId, "IRRIGATION");
        irrigationActuator.setGatewayDeviceId(edgeDevice.getId());
        actuators.add(irrigationActuator);
    }

    private static FogDevice createFogDevice(String nodeName, long mips,
                                             int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {

        List<Pe> peList = new ArrayList<>();
        peList.add(new Pe(0, new PeProvisionerOverbooking(mips)));
        int hostId = FogUtils.generateEntityId();
        long storage = 1000000;
        int bw = 10000;

        PowerHost host = new PowerHost(
                hostId,
                new RamProvisionerSimple(ram),
                new BwProvisionerOverbooking(bw),
                storage,
                peList,
                new StreamOperatorScheduler(peList),
                new FogLinearPowerModel(busyPower, idlePower)
        );

        List<Host> hostList = new ArrayList<>();
        hostList.add(host);

        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double time_zone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.0;
        LinkedList<Storage> storageList = new LinkedList<>();

        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                arch, os, vmm, host, time_zone, cost, costPerMem,
                costPerStorage, costPerBw);

        FogDevice fogdevice = null;
        try {
            fogdevice = new FogDevice(nodeName, characteristics,
                    new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
        } catch (Exception e) {
            e.printStackTrace();
        }

        fogdevice.setLevel(level);
        return fogdevice;
    }

    private static Application createApplication(String appId, int userId) {
        Application application = Application.createApplication(appId, userId);

        // Adding modules for the farming sector sensors
        application.addAppModule("soil_moisture_module", 10);
        application.addAppModule("temperature_module", 10);
        application.addAppModule("humidity_module", 10);
        application.addAppModule("pH_module", 10);
        application.addAppModule("light_intensity_module", 10);

        // Adding a data analyzer module
        application.addAppModule("data_analyzer", 10);

        // Creating edges between sensors, data analyzer, and actuators
        application.addAppEdge("SOIL_MOISTURE", "soil_moisture_module", 1000, 2000, "SOIL_MOISTURE", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("TEMPERATURE", "temperature_module", 2000, 1000, "TEMPERATURE", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("HUMIDITY", "humidity_module", 1000, 500, "HUMIDITY", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("PH", "pH_module", 1000, 500, "PH", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("LIGHT_INTENSITY", "light_intensity_module", 1000, 500, "LIGHT_INTENSITY", Tuple.UP, AppEdge.SENSOR);

        application.addAppEdge("soil_moisture_module", "data_analyzer", 500, 100, "SOIL_ALERT", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("temperature_module", "data_analyzer", 500, 100, "TEMP_ALERT", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("humidity_module", "data_analyzer", 500, 100, "HUMIDITY_ALERT", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("pH_module", "data_analyzer", 500, 100, "PH_ALERT", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("light_intensity_module", "data_analyzer", 500, 100, "LIGHT_ALERT", Tuple.UP, AppEdge.MODULE);

        application.addAppEdge("data_analyzer", "IRRIGATION", 100, 50, "CONTROL_SIGNAL", Tuple.DOWN, AppEdge.ACTUATOR);

        // Adding tuple mappings
        application.addTupleMapping("soil_moisture_module", "SOIL_MOISTURE", "SOIL_ALERT", new FractionalSelectivity(0.7));
        application.addTupleMapping("temperature_module", "TEMPERATURE", "TEMP_ALERT", new FractionalSelectivity(0.7));
        application.addTupleMapping("humidity_module", "HUMIDITY", "HUMIDITY_ALERT", new FractionalSelectivity(0.5));
        application.addTupleMapping("pH_module", "PH", "PH_ALERT", new FractionalSelectivity(0.3));
        application.addTupleMapping("light_intensity_module", "LIGHT_INTENSITY", "LIGHT_ALERT", new FractionalSelectivity(0.5));

        List<AppLoop> loops = new ArrayList<>();
        loops.add(new AppLoop(new ArrayList<String>(){{add("soil_moisture_module");add("data_analyzer");}}));
        loops.add(new AppLoop(new ArrayList<String>(){{add("temperature_module");add("data_analyzer");}}));
        loops.add(new AppLoop(new ArrayList<String>(){{add("humidity_module");add("data_analyzer");}}));
        loops.add(new AppLoop(new ArrayList<String>(){{add("pH_module");add("data_analyzer");}}));
        loops.add(new AppLoop(new ArrayList<String>(){{add("light_intensity_module");add("data_analyzer");}}));

        application.setLoops(loops);

        return application;
    }
}
