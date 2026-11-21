package app.koyura.app.di

import app.koyura.features.downloads.DownloadQueueRepository
import app.koyura.features.library.LibraryRepository
import app.koyura.features.library.LibraryViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * 平台差异模块（expect/actual 闭环在 :shared:app 内）。
 *
 * 现阶段无平台相关依赖，返回空模块占位，后续平台能力（文件授权、
 * 密钥、调度、通知）在此按平台提供。
 */
expect val platformModule: Module

/**
 * 通用装配模块：无会话依赖的纯逻辑/工厂。
 *
 * 会话绑定的仓储（Production*）仍由 [app.koyura.app.ExtensionApp]
 * 在会话打开后构造，透过 Koin parameters 注入 ViewModel，
 * 避免在 Koin 里持有可空全局单例。
 */
val commonAppModule: Module = module {
    // 预留：调度器、用例工厂等无状态依赖在此提供。
}

/**
 * Library 样板模块（阶段 0）。
 *
 * ViewModel 由 Koin 创建、生命周期跟随 NavKey/宿主，
 * 仓储仍来自 CompositionLocal，经 parameters 传入：
 * `koinViewModel(parameters = { parametersOf(repo, downloads) })`。
 */
val libraryViewModelModule: Module = module {
    viewModel { params ->
        LibraryViewModel(
            repository = params.getOrNull<LibraryRepository>(),
            downloads = params.getOrNull<DownloadQueueRepository>(),
        )
    }
}

/** 应用 Koin 模块聚合：平台模块 + 通用 + 各 feature ViewModel。 */
fun appKoinModules(): List<Module> = listOf(
    platformModule,
    commonAppModule,
    libraryViewModelModule,
)
