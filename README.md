# MQTT Waker

MQTT Waker is an Android application that allows you to remotely wake and lock your device's screen via MQTT messages. When specific MQTT commands are received, the app can turn on your device's screen and optionally open a browser URL.

(Disclaimer: Most of the content, including majority of this README, is generated by AI. While I have reviewed all the code and tested it on my own device, I myself am not a seasoned Android developer, and the prompt has been written in a way to target my own usecase specifically, so please use with caution.)

## Features

- Connect to any MQTT broker with optional SSL/TLS support
- Wake device screen remotely via MQTT commands
- Lock device screen remotely via MQTT commands
- Optionally open a specific URL when the device wakes
- Publish screen on/off state changes to a configurable MQTT topic
- Support for custom certificates
- Automatically start on device boot
- Runs as a foreground service for reliability

## Home Assistant

This app can be used to provide a [MQTT Switch](https://www.home-assistant.io/integrations/switch.mqtt/) in Home Assistant for the Android device by simply adding the following configuration to configuration:
```yaml
- mqtt
  - switch:
      command_topic: "<subscribe topic>"
      state_topic: "<publish topic>"
```

## Required Permissions

The app requires the following permissions:

- `INTERNET`: Connect to MQTT brokers
- `SYSTEM_ALERT_WINDOW`: Display over other apps to reliably wake the screen
- `RECEIVE_BOOT_COMPLETED`: Start the service when the device boots
- `FOREGROUND_SERVICE`: Run reliably in the background
- Device Administrator: Required to lock the screen remotely

## Setup

1. Install the app
2. Grant Device Administrator privileges when prompted
3. Grant "Display over other apps" permission when prompted
4. Configure your MQTT broker settings
5. Enable the service

## MQTT Commands

The app subscribes to the configured MQTT topic and responds to these messages:
- `ON`: Turns on the device screen and optionally opens a URL
- `OFF`: Locks the device screen

## MQTT State Publishing

The app will publish the current screen state to the configured publish topic:
- `ON`: Published when the screen turns on
- `OFF`: Published when the screen turns off

The state is published as a retained message with QoS level 1 (at least once). To use this feature, simply configure a publish topic in the app settings.

## Building from Source

This project uses Gradle with Kotlin DSL. To build:

1. Clone the repository
2. Open in Android Studio
3. Build with Gradle

## Privacy

MQTT Waker only connects to the MQTT broker you configure and does not send any data to other servers.

## License

```
Copyright (C) 2025  Xidorn Quan

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```
