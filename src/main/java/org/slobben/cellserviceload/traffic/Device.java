package org.slobben.cellserviceload.traffic;

/** Canvas size in CSS pixels: the frontend uses screen.width by innerHeight - 30. */
public enum Device {
  DESKTOP(1920, 1050),
  PHONE(412, 785);

  final int width;
  final int height;

  Device(int width, int height) {
    this.width = width;
    this.height = height;
  }
}
