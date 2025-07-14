# Weather app with BLE and ESP32 integration
## Description
This project features an Android application integrated with an ESP32 microcontroller using Bluetooth Low Energy (BLE) to deliver real-time weather information.
The Android app allows users to input a city name, which it then sends to the ESP32 via BLE. The ESP32 connects to a weather API over Wi-Fi, retrieves current 
weather data in JSON format and extracts key details such as temperature and wind speed. These details are then packaged into a JSON object and transmitted back 
to the app through BLE for display.

## Key features
- BLE Communication: Seamless two-way data exchange between Android device and ESP32.
- Wi-Fi Connectivity: ESP32 fetches live weather data from an online API.
- JSON Parsing and Packaging: Efficient extraction of temperature and wind data from API responses, which are then repackaged into a JSON object for transmission.
- User-friendly Interface: Simple input and display of weather details on the Android app.
  
## Technologies used
- ESP32 (Arduino IDE)
- Android (Java/Kotlin in Android Studio)
- Bluetooth Low Energy (BLE)
- RESTful Weather API
- JSON parsing libraries
