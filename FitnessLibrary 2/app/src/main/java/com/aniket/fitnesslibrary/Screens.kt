@file:OptIn(ExperimentalMaterial3Api::class)

package com.aniket.fitnesslibrary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------- shared bits ----------------

@Composable
private fun Thumb(url: String?, size: Int = 72) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(8.dp)).then(Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceVariant) {
            Box(contentAlignment = Alignment.Center) { Text("No image", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun ExerciseCard(name: String, category: String, imageUrl: String?, trailing: @Composable () -> Unit = {}, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(imageUrl)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text(category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            trailing()
        }
    }
}

@Composable
private fun ErrorBox(msg: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Something went wrong: $msg", color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun ScreenTitle(text: String) =
    Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
        modifier = Modifier.statusBarsPadding().padding(16.dp))

@Composable
private fun NameDialog(title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, label = { Text("Plan name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onConfirm(name) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------- exercise list ----------------

@Composable
fun ExerciseListScreen(repo: Repository, onOpen: (Int) -> Unit) {
    val vm: ExerciseListViewModel = viewModel(factory = viewModelFactory { initializer { ExerciseListViewModel(repo) } })
    val s by vm.state.collectAsState()
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, s.items.size) { if (nearEnd && s.items.isNotEmpty()) vm.loadMore() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        OutlinedTextField(
            value = s.query, onValueChange = vm::setQuery, singleLine = true,
            placeholder = { Text("Search exercises") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(s.selectedCategory == null, { vm.selectCategory(null) }, { Text("All") }) }
            items(s.categories) { c -> FilterChip(s.selectedCategory == c.id, { vm.selectCategory(c.id) }, { Text(c.name) }) }
        }
        LazyColumn(
            state = listState, modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(s.items) { e -> ExerciseCard(e.name, e.category, e.imageUrl) { onOpen(e.id) } }
            if (s.loading) item { Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator() } }
            if (s.error != null) item { ErrorBox(s.error!!) { vm.loadMore() } }
            if (!s.loading && s.error == null && s.items.isEmpty())
                item { Text("No exercises found.", Modifier.padding(16.dp)) }
        }
    }
}

// ---------------- exercise detail ----------------

@Composable
fun ExerciseDetailScreen(repo: Repository, id: Int, onBack: () -> Unit) {
    val vm: ExerciseDetailViewModel = viewModel(key = "detail$id", factory = viewModelFactory { initializer { ExerciseDetailViewModel(repo, id) } })
    val s by vm.state.collectAsState()
    val plans by vm.plans.collectAsState(initial = emptyList())
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.exercise?.name ?: "Exercise", maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                s.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                s.error != null -> ErrorBox(s.error!!) { vm.load() }
                else -> s.exercise?.let { e ->
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (e.images.isNotEmpty()) item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(e.images) { u -> Thumb(u, 220) }
                            }
                        }
                        item { AssistChip(onClick = {}, label = { Text(e.category) }) }
                        if (e.muscles.isNotEmpty()) item { Text("Muscles: ${e.muscles.joinToString()}") }
                        if (e.equipment.isNotEmpty()) item { Text("Equipment: ${e.equipment.joinToString()}") }
                        item { Text(e.description.ifBlank { "No description available." }) }
                        item { Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("Add to workout plan") } }
                    }
                }
            }
        }
    }

    if (showAdd) AlertDialog(
        onDismissRequest = { showAdd = false }, title = { Text("Add to plan") },
        text = {
            LazyColumn {
                items(plans) { p ->
                    TextButton(onClick = {
                        vm.addToPlan(p.id); showAdd = false
                        scope.launch { snack.showSnackbar("Added to ${p.name}") }
                    }, modifier = Modifier.fillMaxWidth()) { Text(p.name) }
                }
                item {
                    TextButton(onClick = { showAdd = false; showNew = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("New plan")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showAdd = false }) { Text("Close") } },
    )
    if (showNew) NameDialog("New plan", { showNew = false }) { name ->
        vm.createPlanAndAdd(name); showNew = false
        scope.launch { snack.showSnackbar("Added to $name") }
    }
}

// ---------------- plans ----------------

@Composable
fun PlansScreen(repo: Repository, onOpen: (Long) -> Unit) {
    val plans by repo.plans().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showNew by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ScreenTitle("Workout plans") }
            IconButton(onClick = { showNew = true }, modifier = Modifier.statusBarsPadding()) { Icon(Icons.Default.Add, "New plan") }
        }
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (plans.isEmpty()) item { Text("No plans yet. Tap + to create one.") }
            items(plans) { p ->
                Card(onClick = { onOpen(p.id) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold)
                            Text("${p.exerciseCount} exercises", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { scope.launch { repo.deletePlan(p.id) } }) { Icon(Icons.Default.Delete, "Delete plan") }
                    }
                }
            }
        }
    }
    if (showNew) NameDialog("New plan", { showNew = false }) { name ->
        scope.launch { repo.createPlan(name) }; showNew = false
    }
}

@Composable
fun PlanDetailScreen(repo: Repository, planId: Long, onBack: () -> Unit) {
    val plan by repo.plan(planId).collectAsState(initial = null)
    val exercises by repo.planExercises(planId).collectAsState(initial = emptyList())
    var done by remember { mutableStateOf(setOf<Int>()) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(plan?.name ?: "Plan") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            Button(
                enabled = done.isNotEmpty(),
                onClick = {
                    val n = done.size
                    scope.launch {
                        repo.logWorkout(plan?.name ?: "Workout", n)
                        done = emptySet()
                        snack.showSnackbar("Workout logged ($n exercises)")
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
            ) { Text("Finish workout (${done.size} done)") }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (exercises.isEmpty()) item { Text("Empty plan. Open an exercise and tap \"Add to workout plan\".") }
            items(exercises) { e ->
                ExerciseCard(e.name, e.category, e.imageUrl, trailing = {
                    Checkbox(e.exerciseId in done, { c -> done = if (c) done + e.exerciseId else done - e.exerciseId })
                    IconButton(onClick = { scope.launch { repo.removeFromPlan(planId, e.exerciseId) }; done = done - e.exerciseId }) {
                        Icon(Icons.Default.Delete, "Remove")
                    }
                }) { done = if (e.exerciseId in done) done - e.exerciseId else done + e.exerciseId }
            }
        }
    }
}

// ---------------- history ----------------

@Composable
fun HistoryScreen(repo: Repository) {
    val items by repo.history().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val fmt = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Completed workouts")
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (items.isEmpty()) item { Text("Nothing logged yet. Finish a plan to see it here.") }
            items(items) { c ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.planName, fontWeight = FontWeight.SemiBold)
                            Text("${c.exerciseCount} exercises • ${fmt.format(Date(c.completedAt))}", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { scope.launch { repo.deleteHistory(c.id) } }) { Icon(Icons.Default.Delete, "Delete") }
                    }
                }
            }
        }
    }
}
