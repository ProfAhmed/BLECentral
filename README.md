### What we do
* Central scans for Peripheral and connects to it
* Central reads a value from Peripheral
* Central writes a value to Peripheral
* Peripheral notifies Central that a value has changed

Each BLE Central is compatible with each BLE Peripheral, because they use the same service and characteristics UUIDs.

### How to run
To run and see it working, you need 2 Android devices supporting Bluetooth Low Energy:
* one device for BLE Central app 
* another device for BLE Peripheral app 

...and some development tools:
* Android Studio - for Android project


## Table of UUIDs
| Name                        | UUID                                 |
|-----------------------------|--------------------------------------|
| Service                     | 25AE1441-05D3-4C5B-8281-93D4E07420CF |
| Characteristic for read     | 25AE1442-05D3-4C5B-8281-93D4E07420CF |
| Characteristic for write    | 25AE1443-05D3-4C5B-8281-93D4E07420CF |
| Characteristic for indicate | 25AE1444-05D3-4C5B-8281-93D4E07420CF |

## BLE Peripheral 
Use The Ble_Peripheral APK attached for testing

## BLE Central (all platforms)
Clone and run the project to use as a Central Device

## Notes
This Demo work when the App on Foreground, In Real Production should be work on both foreground and background
