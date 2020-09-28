# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).


## [0.2.0] - 2020-09-28

### Changed
- [Migrate](https://github.com/google/ksp/blob/master/old-ksp-release.md) ksp 
- Improve kapt error messaging
- Build performance improvements
  
### Added
- Allow annotating interfaces with `@Component`
- Support `javax.inject.Qualifer`

### Fixed
- Fixed companion generation (`me.tatarka.inject.generateCompanionExtensions=true`) for ksp
- Throw error when parent component is missing val instead of generating incorrect code.

## [0.1.0] - 2020-09-17
- Initial Release
