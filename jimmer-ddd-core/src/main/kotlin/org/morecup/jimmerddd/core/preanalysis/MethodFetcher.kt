package org.morecup.jimmerddd.core.preanalysis

import org.babyfish.jimmer.sql.fetcher.impl.FetcherImpl
import org.babyfish.jimmer.sql.fetcher.impl.FetcherImplementor
import org.morecup.jimmerddd.core.annotation.AggregatedField
import org.morecup.jimmerddd.core.annotation.AggregationType
import org.objectweb.asm.Type
import java.util.concurrent.ConcurrentHashMap

// 定义一个数据类作为缓存的键
private data class CacheKey<T : Any>(val entityClazz: Class<T>, val methodInfo: MethodInfo)

private val analysisMethodFetcherCache: MutableMap<CacheKey<*>, FetcherImplementor<*>> = ConcurrentHashMap()

fun <T : Any> analysisMethodFetcher(entityClazz: Class<T>, methodInfo: MethodInfo): FetcherImplementor<T> {
    val cacheKey = CacheKey(entityClazz, methodInfo)
    return analysisMethodFetcherCache.computeIfAbsent(cacheKey) { t ->
        val fetcherImpl: FetcherImpl<T> = FetcherImpl(entityClazz)
        val analyzeMethods: MutableSet<MethodInfo> = ClassMethodAnalyzer.analyzeMethods(t.methodInfo)
        val neededGetFieldNameSet = analyzeMethods.filter {
//        it.ownerClass == function.javaMethod?.declaringClass?.name&&
            (it.desc.startsWith("()")||it.desc.startsWith("(Z)"))&&
                    it.desc!="()V" }.map {
            if (it.desc.startsWith("(Z)")){ it.desc = it.desc.replace("(Z)","()")}
            if (it.ownerClass.endsWith("Draft")){ it.ownerClass = it.ownerClass.substring(0, it.ownerClass.length - "Draft".length)}
            if (it.name.startsWith("get")){ it.name = it.name.substring(3).replaceFirstChar { it.lowercase() }}
            if (it.desc.endsWith("Draft;")){ it.desc = it.desc.substring(0, it.desc.length - "Draft;".length) + ";"}
            if (it.ownerClass == methodInfo.ownerClass){ it.ownerClass = entityClazz.name }
            it
        }.toHashSet()

        asmMethodInfoToFetcher(fetcherImpl, neededGetFieldNameSet)
    } as FetcherImplementor<T>
}

private fun <T> asmMethodInfoToFetcher(baseFetcher:FetcherImplementor<T>, methodInfoSet: HashSet<MethodInfo>, loadedClassList:List<Class<*>> = arrayListOf()): FetcherImplementor<T> {
    var fetcher:FetcherImplementor<T> = baseFetcher
    for (prop in fetcher.immutableType.props.values) {
        val methodInfo = MethodInfo(fetcher.immutableType.javaClass.name,prop.name, Type.getMethodDescriptor(Type.getType(prop.returnClass)))

        if (!methodInfoSet.contains(methodInfo)) {
            continue
        }
        val annotations = prop.annotations
        val aggregatedField = annotations.filterIsInstance<AggregatedField>().firstOrNull()
//        val lazy = annotations.filterIsInstance<Lazy>().firstOrNull()
//        if (lazy != null && lazy.prepareLoadMode == PrepareLoadMode.Unload) {
//            continue
//        }
        if (aggregatedField == null|| aggregatedField.type == AggregationType.AGGREGATED) {
            if (prop.targetType!=null){
                if (!loadedClassList.contains(prop.targetType.javaClass)){
                    val fields = asmMethodInfoToFetcher(
                        FetcherImpl(prop.targetType.javaClass),
                        methodInfoSet,
                        loadedClassList + fetcher.immutableType.javaClass
                    )
                    fetcher = fetcher.add(prop.name,fields)
                }else{
                    continue
                }
            }else{
                fetcher = fetcher.add(prop.name)
            }
        }else if (aggregatedField.type == AggregationType.ID_ONLY){
            fetcher = fetcher.add(prop.name)
        }else if (aggregatedField.type == AggregationType.NON_AGGREGATED){
            continue
        }
    }
    return fetcher
}