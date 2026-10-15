package dev.joyfin.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.data.Person
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.theme.MotionTokens
import dev.joyfin.ui.theme.Palette

@Composable
fun PeopleRail(
    people: List<Person>,
    images: ArtworkSource,
    onOpen: (Person) -> Unit,
    state: LazyListState = rememberLazyListState(),
    itemFocus: @Composable (Person) -> Modifier = {
        Modifier.focusTarget("person:${it.id}:${it.name}")
    },
) {
    if (people.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle("演员与主创", Modifier.padding(horizontal = LocalDeviceLayout.current.gutter))
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = LocalDeviceLayout.current.gutter),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(people, key = { "${it.id}:${it.name}:${it.role}" }) { person ->
                val tv = LocalDeviceLayout.current.television
                FocusSurface(
                    { onOpen(person) },
                    itemFocus(person)
                        .padding(vertical = MotionTokens.PeopleCardVerticalReserve)
                        .width(104.dp),
                    transparent = true,
                    focusedScale = if (tv) MotionTokens.CardFocusedScale else MotionTokens.FixedScale,
                ) {
                    Column(
                        Modifier.padding(5.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Artwork(
                            images.person(person),
                            images,
                            Modifier.size(88.dp)
                                .clip(CircleShape),
                            person.name.take(1),
                        )
                        Text(
                            person.name,
                            color = Palette.Text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 22.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Caption(
                            if (person.role.isNotBlank()) "饰 ${person.role}"
                            else
                                when (person.type) {
                                    "Director" -> "导演"
                                    "Actor" -> "演员"
                                    "Writer" -> "编剧"
                                    else -> person.type
                                },
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
