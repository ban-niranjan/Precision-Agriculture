package org.fog.test.perfeval;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;

import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;

import org.fog.entities.Actuator;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;

import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementMapping;

import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

public class PrecisionAgriFogAndCloud {

    // -------------------------
    // Set mode here:
    // false => FOG mode (sensor-processing on edges, analyzer on cloud)
    // true  => CLOUD mode (all modules on cloud)
    // -------------------------
    private static final boolean CLOUD = true;

    // Number of field zones / edge devices
    private static final int NUM_ZONES = 4;

    // Containers
    private static List<FogDevice> fogDevices = new ArrayList<>();
    private static List<Sensor> sensors = new ArrayList<>();
    private static List<Actuator> actuators = new ArrayList<>();

    public static void main(String[] args) {
        try {
            Log.disable(); // reduce CloudSim logs

            int numUsers = 1;
            Calendar calendar = Calendar.getInstance();
            boolean traceFlag = false;
            CloudSim.init(numUsers, calendar, traceFlag);

            FogBroker broker = new FogBroker("broker");

            // create application
            String appId = "precision_agri";
            Application application = createApplication(appId, broker.getId());
            application.setUserId(broker.getId());

            // create fog devices, sensors, actuators
            createFogDevices(broker.getId(), appId);

            // create module mapping depending on CLOUD flag
            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();

            if (CLOUD) {
                System.out.println("========== RUNNING MODE: CLOUD ==========");
                // all modules on cloud
                moduleMapping.addModuleToDevice("soil_moisture_module", "cloud");
                moduleMapping.addModuleToDevice("temperature_module", "cloud");
                moduleMapping.addModuleToDevice("humidity_module", "cloud");
                moduleMapping.addModuleToDevice("pH_module", "cloud");
                moduleMapping.addModuleToDevice("light_intensity_module", "cloud");
                moduleMapping.addModuleToDevice("data_analyzer", "cloud");
            } else {
                System.out.println("========== RUNNING MODE: FOG ==========");
                // sensor-processing modules on each edge
                for (FogDevice fd : fogDevices) {
                    if (fd.getName().startsWith("edge-zone")) {
                        moduleMapping.addModuleToDevice("soil_moisture_module", fd.getName());
                        moduleMapping.addModuleToDevice("temperature_module", fd.getName());
                        moduleMapping.addModuleToDevice("humidity_module", fd.getName());
                        moduleMapping.addModuleToDevice("pH_module", fd.getName());
                        moduleMapping.addModuleToDevice("light_intensity_module", fd.getName());
                    }
                }
                // data_analyzer placed in cloud (hybrid: edge preprocessing + cloud analysis)
                moduleMapping.addModuleToDevice("data_analyzer", "cloud");
            }

            Controller controller = new Controller("master-controller", fogDevices, sensors, actuators);

            if (CLOUD) {
                controller.submitApplication(application, new ModulePlacementMapping(fogDevices, application, moduleMapping));
            } else {
                controller.submitApplication(application, new ModulePlacementEdgewards(fogDevices, sensors, actuators, application, moduleMapping));
            }

            // record simulation start (some iFogSim forks use TimeKeeper only for start time)
            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());

            // run
            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            System.out.println("\n========== SIMULATION FINISHED ==========");
            System.out.println("Check console for per-device energies and tuple delays (if printed by your iFogSim version).");
            System.out.println("Mode used: " + (CLOUD ? "CLOUD" : "FOG"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // -------------------------
    // Create fog/cloud topology
    // -------------------------
    private static void createFogDevices(int userId, String appId) throws Exception {
        // Cloud
        FogDevice cloud = createFogDevice("cloud", 44800, 40000, 10000, 10000, 0, 0.01, 107.339, 83.4333);
        cloud.setParentId(-1);
        cloud.setUplinkLatency(0); // cloud is root
        fogDevices.add(cloud);

        // Proxy / regional gateway
        FogDevice proxy = createFogDevice("proxy-server", 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333);
        proxy.setParentId(cloud.getId());
        proxy.setUplinkLatency(80); // e.g., 80 ms proxy->cloud
        fogDevices.add(proxy);

        // Edge devices (farm controllers)
        for (int i = 1; i <= NUM_ZONES; i++) {
            FogDevice edge = createFogDevice("edge-zone-" + i, 2800, 2000, 1000, 1000, 2, 0.0, 87.53, 82.44);
            edge.setParentId(proxy.getId());
            edge.setUplinkLatency(2); // 2 ms edge->proxy
            fogDevices.add(edge);

            // link sensors and actuators and set latencies (CRITICAL)
            linkSensorsAndActuatorsToFogDevices(userId, appId, edge);
        }
    }

    private static void linkSensorsAndActuatorsToFogDevices(int userId, String appId, FogDevice edgeDevice) {
        // Create sensors and set gateway (latency is mandatory for ModulePlacementEdgewards)
        Sensor sm = new Sensor("sm-" + edgeDevice.getName(), "SOIL_MOISTURE", userId, appId, new DeterministicDistribution(5));
        sm.setGatewayDeviceId(edgeDevice.getId());
        sm.setLatency(1.0);
        sensors.add(sm);

        Sensor tp = new Sensor("tp-" + edgeDevice.getName(), "TEMPERATURE", userId, appId, new DeterministicDistribution(6));
        tp.setGatewayDeviceId(edgeDevice.getId());
        tp.setLatency(1.0);
        sensors.add(tp);

        Sensor hm = new Sensor("hm-" + edgeDevice.getName(), "HUMIDITY", userId, appId, new DeterministicDistribution(7));
        hm.setGatewayDeviceId(edgeDevice.getId());
        hm.setLatency(1.0);
        sensors.add(hm);

        Sensor ph = new Sensor("ph-" + edgeDevice.getName(), "PH", userId, appId, new DeterministicDistribution(4));
        ph.setGatewayDeviceId(edgeDevice.getId());
        ph.setLatency(1.0);
        sensors.add(ph);

        Sensor light = new Sensor("light-" + edgeDevice.getName(), "LIGHT_INTENSITY", userId, appId, new DeterministicDistribution(5));
        light.setGatewayDeviceId(edgeDevice.getId());
        light.setLatency(1.0);
        sensors.add(light);

        // Actuator for irrigation — actuator latency also required
        Actuator irrigationActuator = new Actuator("irrigation-" + edgeDevice.getName(), userId, appId, "IRRIGATION");
        irrigationActuator.setGatewayDeviceId(edgeDevice.getId());
        irrigationActuator.setLatency(1.0);
        actuators.add(irrigationActuator);
    }

    // -------------------------
    // FogDevice factory (compatible with iFogSim 1.x)
    // -------------------------
    private static FogDevice createFogDevice(String nodeName, long mips, int ram, long upBw, long downBw,
                                             int level, double ratePerMips, double busyPower, double idlePower) {
        List<Pe> peList = new ArrayList<>();
        peList.add(new Pe(0, new PeProvisionerOverbooking(mips)));

        int hostId = FogUtils.generateEntityId();

        PowerHost host = new PowerHost(hostId,
                new RamProvisionerSimple(ram),
                new BwProvisionerOverbooking(10000),
                1000000,
                peList,
                new StreamOperatorScheduler(peList),
                new FogLinearPowerModel(busyPower, idlePower)
        );

        List<Host> hostList = new ArrayList<>();
        hostList.add(host);

        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                "x86", "Linux", "Xen", host, 10.0, 3.0, 0.05, 0.001, 0.0
        );

        FogDevice fogdevice = null;
        try {
            fogdevice = new FogDevice(nodeName, characteristics, new AppModuleAllocationPolicy(hostList),
                    new LinkedList<Storage>(), 10, upBw, downBw, 0, ratePerMips);
        } catch (Exception e) {
            e.printStackTrace();
        }

        fogdevice.setLevel(level);
        return fogdevice;
    }

    // -------------------------
    // Application (DAG)
    // -------------------------
    private static Application createApplication(String appId, int userId) {
        Application application = Application.createApplication(appId, userId);

        // add modules
        application.addAppModule("soil_moisture_module", 10);
        application.addAppModule("temperature_module", 10);
        application.addAppModule("humidity_module", 10);
        application.addAppModule("pH_module", 10);
        application.addAppModule("light_intensity_module", 10);

        application.addAppModule("data_analyzer", 20);

        // sensor -> module edges
        application.addAppEdge("SOIL_MOISTURE", "soil_moisture_module", 1000, 1000, "SOIL_MOISTURE", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("TEMPERATURE", "temperature_module", 1000, 1000, "TEMPERATURE", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("HUMIDITY", "humidity_module", 1000, 1000, "HUMIDITY", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("PH", "pH_module", 1000, 1000, "PH", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("LIGHT_INTENSITY", "light_intensity_module", 1000, 1000, "LIGHT_INTENSITY", Tuple.UP, AppEdge.SENSOR);

        // module -> analyzer edges
        application.addAppEdge("soil_moisture_module", "data_analyzer", 500, 200, "SOIL_ALERT", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("temperature_module", "data_analyzer", 500, 200, "TEMP_ALERT", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("humidity_module", "data_analyzer", 500, 200, "HUMIDITY_ALERT", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("pH_module", "data_analyzer", 500, 200, "PH_ALERT", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("light_intensity_module", "data_analyzer", 500, 200, "LIGHT_ALERT", Tuple.UP, AppEdge.MODULE);

        // analyzer -> actuator (control signal)
        application.addAppEdge("data_analyzer", "IRRIGATION", 100, 50, "CONTROL_SIGNAL", Tuple.DOWN, AppEdge.ACTUATOR);

        // tuple mappings (basic)
        application.addTupleMapping("soil_moisture_module", "SOIL_MOISTURE", "SOIL_ALERT", new FractionalSelectivity(1.0));
        application.addTupleMapping("temperature_module", "TEMPERATURE", "TEMP_ALERT", new FractionalSelectivity(1.0));
        application.addTupleMapping("humidity_module", "HUMIDITY", "HUMIDITY_ALERT", new FractionalSelectivity(1.0));
        application.addTupleMapping("pH_module", "PH", "PH_ALERT", new FractionalSelectivity(1.0));
        application.addTupleMapping("light_intensity_module", "LIGHT_INTENSITY", "LIGHT_ALERT", new FractionalSelectivity(1.0));

        // loops (for delay measurement if your iFogSim supports it)
        List<AppLoop> loops = new ArrayList<>();
        loops.add(new AppLoop(new ArrayList<String>() {{ add("soil_moisture_module"); add("data_analyzer"); }}));
        loops.add(new AppLoop(new ArrayList<String>() {{ add("temperature_module"); add("data_analyzer"); }}));
        loops.add(new AppLoop(new ArrayList<String>() {{ add("humidity_module"); add("data_analyzer"); }}));
        loops.add(new AppLoop(new ArrayList<String>() {{ add("pH_module"); add("data_analyzer"); }}));
        loops.add(new AppLoop(new ArrayList<String>() {{ add("light_intensity_module"); add("data_analyzer"); }}));
        application.setLoops(loops);

        return application;
    }
}





