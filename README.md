# Precision Agriculture using Fog and Cloud Computing

This project implements and evaluates a fog-enabled IoT architecture for precision agriculture using the iFogSim simulation toolkit. The system models real-time agricultural monitoring using multiple sensors and compares fog and cloud computing deployments based on performance metrics.

---

## 📌 Project Overview

Modern agriculture requires real-time decision-making to optimize water usage, fertilizers, and crop health. Traditional cloud-based solutions often suffer from high latency and excessive network usage. This project addresses these challenges by leveraging fog computing to process data closer to the field.

The system is simulated using **Java and iFogSim**, enabling a controlled comparison between **Fog-based** and **Cloud-based** architectures.

---

## 🧠 System Architecture

The simulated architecture consists of:
- **Sensor Layer**: Soil moisture, temperature, humidity, pH, and light intensity sensors
- **Edge/Fog Layer**: Zone-level fog nodes for local data processing
- **Gateway Layer**: Proxy server for aggregation
- **Cloud Layer**: Centralized analytics and data storage
- **Actuator Layer**: Irrigation control systems

---

## ⚙️ Technologies Used

- Java  
- iFogSim  
- CloudSim  
- Fog Computing  
- Internet of Things (IoT)  

---

## 🔬 Simulation Scenarios

Two execution modes are evaluated:

1. **Cloud Mode**
   - All processing modules deployed on the cloud
   - Higher latency and network usage

2. **Fog Mode**
   - Sensor-processing modules deployed on fog nodes
   - Reduced latency, lower network traffic, faster response

---

## 📊 Performance Metrics

The following metrics are analyzed:
- End-to-end latency
- Execution time
- Energy consumption
- Network usage

Graphs and tables are generated to compare fog and cloud performance.

---

## ✅ Key Findings

- Fog computing significantly reduces latency for time-critical agricultural operations
- Network usage is lower in fog-based deployments
- Fog enables faster response for irrigation control compared to cloud-only models
- Cloud is more suitable for large-scale historical analysis

---

## 🚀 How to Run

1. Import the project into IntelliJ IDEA or Eclipse
2. Add iFogSim and CloudSim dependencies
3. Run the main simulation class
4. Toggle between Fog and Cloud modes using a configuration flag
5. Observe results in the console output

---

## 📚 Use Case

- Smart irrigation systems  
- Real-time agricultural monitoring  
- IoT-based decision support systems  
- Fog vs Cloud performance evaluation  

---

## 📄 License

This project is intended for academic and research purposes.
