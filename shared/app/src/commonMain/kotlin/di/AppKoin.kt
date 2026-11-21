package app.koyura.app.di

import app.koyura.features.downloads.DownloadQueueRepository
import app.koyura.features.downloads.DownloadsStoreFactory
import app.koyura.features.downloads.DownloadsStoreHolder
import app.koyura.features.library.LibrarySession
import app.koyura.features.library.LibraryViewModel
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
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
 * ViewModel 由 Koin 创建、生命周期跟随 NavKey 域化的 ViewModelStore；
 * 包入的 [LibrarySession] 由调用方（装配层持有的同一实例）经 parameters 传入，
 * 不在 Koin 里持有可空全局单例避免分叉。
 */
val libraryViewModelModule: Module = module {
    viewModel { params ->
        LibraryViewModel(session = params.get<LibrarySession>())
    }
}

/** 应用 Koin 模块聚合：平台模块 + 通用 + 各 feature ViewModel/Store。 */
fun appKoinModules(): List<Module> = listOf(
    platformModule,
    commonAppModule,
    libraryViewModelModule,
    downloadsStoreModule,
)

/**
 * 下载 Store 模块（阶段 1）。
 *
 * 单例 [StoreFactory] 供各 Store 共用；[DownloadsStoreFactory] 单例；
 * Holder 按参数给仓储（会话绑定不在 Koin 里持有可空全局单例与 Library 一致）。
 */
val downloadsStoreModule: Module = module {
    single<StoreFactory> { DefaultStoreFactory() }
    single { DownloadsStoreFactory(get()) }
    viewModel { params ->
        DownloadsStoreHolder(get<DownloadsStoreFactory>().create(params.get<DownloadQueueRepository>()))
    }
}
