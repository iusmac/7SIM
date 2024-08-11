# Changelog

## [2.0.0](https://github.com/iusmac/7SIM/compare/v1.2.2-beta...v2.0.0) (2024-08-11)


### :sparkles: Features

* **#1:** add an arbitrary number of schedules per SIM card ([#24](https://github.com/iusmac/7SIM/issues/24)) ([97d5aff](https://github.com/iusmac/7SIM/commit/97d5affbfd93464324c3c54a42eb181fa75503de))


### :hammer_and_wrench: Miscellaneous Chores

* release 2.0.0 ([e10b842](https://github.com/iusmac/7SIM/commit/e10b8425b1dac4becd173ef90392d9784059618f))

## [1.2.2-beta](https://github.com/iusmac/7SIM/compare/v1.2.1-beta...v1.2.2-beta) (2024-08-08)


### :bug: Bug Fixes

* don't force reset user SIM enabled state after system time alter ([5b414e9](https://github.com/iusmac/7SIM/commit/5b414e937399527d443b9a1bf6307d606df8a0e9))
* ignore carrier config changes during early boot before syncing SIM cards ([75fbe4b](https://github.com/iusmac/7SIM/commit/75fbe4b1a57262a96926830d951362f841586df7))

## [1.2.1-beta](https://github.com/iusmac/7SIM/compare/v1.2.0-beta...v1.2.1-beta) (2024-06-24)


### :bug: Bug Fixes

* handle SIM PIN decryption on devices without FBE support ([6cb9afa](https://github.com/iusmac/7SIM/commit/6cb9afa7ff06a1549d4587f5b59df9276c450f44))

## [1.2.0-beta](https://github.com/iusmac/7SIM/compare/v1.1.0-beta...v1.2.0-beta) (2024-06-02)


### :sparkles: Features

* **#2:** automatically supply the PIN code to unlock the SIM card ([#20](https://github.com/iusmac/7SIM/issues/20)) ([bf404f6](https://github.com/iusmac/7SIM/commit/bf404f62a51f831cf8e6f3a753800166c9c24a13))


### :wrench: Feature Tweaks

* get the background restriction option faster ([#22](https://github.com/iusmac/7SIM/issues/22)) ([c2c64e4](https://github.com/iusmac/7SIM/commit/c2c64e4f82b5bf89369664146905346558cc54b2))

## [1.1.0-beta](https://github.com/iusmac/7SIM/compare/v1.0.3-beta...v1.1.0-beta) (2024-05-01)


### :sparkles: Features

* **#15:** adapt to Android 14.0 ([#16](https://github.com/iusmac/7SIM/issues/16)) ([e15dda0](https://github.com/iusmac/7SIM/commit/e15dda0ff164878cee05c5a914c60f49bd3c9a17))

## [1.0.3-beta](https://github.com/iusmac/7SIM/compare/v1.0.2-beta...v1.0.3-beta) (2024-04-29)


### :wrench: Feature Tweaks

* **ui/CollapsingToolbar:** enforce fade collapse effect for the title & header content background color ([#17](https://github.com/iusmac/7SIM/issues/17)) ([43becdb](https://github.com/iusmac/7SIM/commit/43becdb92586d506dd8b97422710bc6a67f6327c))

## [1.0.2-beta](https://github.com/iusmac/7SIM/compare/v1.0.1-beta...v1.0.2-beta) (2024-04-11)


### :bug: Bug Fixes

* **#12:** resume scheduler after background restriction removed ([#13](https://github.com/iusmac/7SIM/issues/13)) ([3644913](https://github.com/iusmac/7SIM/commit/3644913300f188c7e5ab3e195301227a01f934f5))

## [1.0.1-beta](https://github.com/iusmac/7SIM/compare/v1.0.0-beta...v1.0.1-beta) (2024-04-10)


### :bug: Bug Fixes

* **#5:** fix crash on Android 14 due to QPR2 API changes ([#9](https://github.com/iusmac/7SIM/issues/9)) ([5b0e4ba](https://github.com/iusmac/7SIM/commit/5b0e4ba920d076970aa33e25e680747eb42f4cfd))
* **#7:** make scheduler respect user SIM enabled/disabled state if time matches current system time ([#11](https://github.com/iusmac/7SIM/issues/11)) ([1e63492](https://github.com/iusmac/7SIM/commit/1e634928b2a04c86379cb398777cd33fddb338e8))

## 1.0.0-beta (2024-04-08)


### :hammer_and_wrench: Miscellaneous Chores

* release 1.0.0-beta ([2e32df2](https://github.com/iusmac/7SIM/commit/2e32df2c80fa7ec779a7b724735aa6b260a8fbfb))
