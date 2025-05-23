package org.morecup.jimmerddd.core

import org.babyfish.jimmer.sql.kt.KSqlClient
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener

@Configuration
open class DDDConfig(
    // 注入 KSqlClient（假设已由其他模块定义）
    private val kSqlClient: KSqlClient
) {

    // 监听 Spring 上下文刷新完成事件
    @EventListener(ContextRefreshedEvent::class)
    fun onContextRefreshed() {
        // 配置 JimmerDDDConfig 的 findByIdFunction
        JimmerDDDConfig.setFindByIdFunction{ fetcher, id ->
            kSqlClient.findById(fetcher, id)
        }
    }
}