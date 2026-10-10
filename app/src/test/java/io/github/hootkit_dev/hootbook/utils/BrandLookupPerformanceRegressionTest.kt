package io.github.hootkit_dev.hootbook.utils

import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.common.icons.BrandIcons
import org.junit.Assert.*
import org.junit.Test

class BrandLookupPerformanceRegressionTest {
    @Test fun longerBrandWinsOverGenericBrand() {
        assertEquals(R.drawable.ic_brand_google_pay, BrandIcons.getIconResource("GOOGLE PAY purchase"))
        assertEquals(R.drawable.ic_brand_youtube_music, BrandIcons.getIconResource("YouTube Music subscription"))
        assertEquals(R.drawable.ic_brand_google, BrandIcons.getIconResource("Google"))
    }

    @Test fun shortKeysOnlyMatchWholeWords() {
        assertEquals(R.drawable.ic_brand_x, BrandIcons.getIconResource("X Premium"))
        assertNull(BrandIcons.getIconResource("Exxon fuel"))
        assertEquals(R.drawable.ic_brand_qq, BrandIcons.getIconResource("QQ会员"))
        assertNull(BrandIcons.getIconResource("Aqqa"))
    }

    @Test fun chineseNamesFindTheirBrand() {
        assertEquals(R.drawable.ic_brand_alipay, BrandIcons.getIconResource("支付宝-余额宝转入"))
        assertEquals(R.drawable.ic_brand_meituan, BrandIcons.getIconResource("美团外卖"))
        assertEquals(R.drawable.ic_brand_starbucks, BrandIcons.getIconResource("星巴克（南京西路店）"))
        assertEquals(R.drawable.ic_brand_netease_cloud_music, BrandIcons.getIconResource("网易云音乐会员"))
        assertEquals(R.drawable.ic_brand_china_unicom, BrandIcons.getIconResource("中国联通话费充值"))
        assertEquals(R.drawable.ic_brand_amap, BrandIcons.getIconResource("高德打车"))
        assertEquals(R.drawable.ic_brand_waldorf_astoria, BrandIcons.getIconResource("Waldorf Astoria Shanghai"))
    }

    @Test fun emptyAndUnknownNamesRetainFallback() {
        assertNull(BrandIcons.getIconResource(""))
        assertNull(BrandIcons.getIconResource("qwerty 987654"))
    }

    @Test fun repeatedLookupHasStablePrecedence() {
        repeat(1000) {
            assertEquals(R.drawable.ic_brand_google_pay, BrandIcons.getIconResource("Google Pay"))
            assertEquals(R.drawable.ic_brand_youtube_music, BrandIcons.getIconResource("YouTube Music"))
        }
    }
}
