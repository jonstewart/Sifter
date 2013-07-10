
var g_OpenIndices = [];
var g_StartSearch;
var g_CurQueryId = '';

//$('#myModal').modal()

function cellData(n) {
  var a = new Array();
  for (var i = 0; i < n; ++i) {
    a.push({l: 0.1 + (Math.random() * 0.8), s: 0.25 + (Math.random() * 0.5)});
  }
  return a;
}

var CELL_HIGHLIGHT_STROKE_WIDTH = 2;
var CELL_DIMENSION = 20;

function cellClick(evt) {
  var clicked = $(this);
  var cell = clicked.attr("cell");
  var x  = clicked.attr("x");
  $("#searchBox").val("cell:\"" + cell + "\"");
  $("#searchQuery").submit();
}

function cellMouseover(evt) {
  $(this).attr("stroke-width", CELL_HIGHLIGHT_STROKE_WIDTH);
  $('#cell-info').html($(this).text());
}

function cellMouseout(evt) {
  $(this).attr("stroke-width", 0);
}

function saturation(cell, max) {
  // low stdDev => high saturation
  return (-100 * (cell.stdDev - max)) / max;
}

function luminance(cell, max) {
  if (cell.num == 0.0) {
    return 100.0;
  }
  else {
    var logRatio = Math.log(cell.num) / Math.log(max)
    // var logRatio = cell.num / max;
    return 100 - ((100 * logRatio) * .7) + .2; // keep luminance in .2–.9
  }
}

function cellInfo(cell, i) {
  return "Cell " + i + ". " + cell.num + " documents, region " + cell.region
    + ". Std Dev = " + cell.stdDev + (cell.maxima ? ", Maxima": "")
    + ". Top terms: " + cell.topTerms.join(", ");
}

function drawSOM(som) {
//  d3.selection().prototype.popover = $.popover.Constructor();
  var rows = som.height;
  var cols = som.width;
  var cellHeight = CELL_DIMENSION;
  var cellWidth = CELL_DIMENSION;
//  var cells = cellData(rows * cols);
  var mapDiv = d3.select("#map");
  var svg = mapDiv.append("svg");
  var rects = svg.selectAll("rect").data(som.cells).enter();
  rects.append("rect")
    .style("fill", function(d, i) {
      var hsl = "hsl(" + 0 + ", " + saturation(d, som.maxCellStd) + "%, " + luminance(d, som.maxCellNum) + "%)";
      console.log("i = " + i + ", color = " + hsl);
      return hsl;
    })
    .attr("cell", function(d, i) { return i; })
    .attr("x", function(d, i) { return (i % cols) * cellWidth; })
    .attr("y", function(d, i) { return Math.floor(i / rows) * cellHeight; })
    .attr("width", cellWidth)
    .attr("height", cellHeight)
    .attr("data-content", cellInfo)
    .attr("data-title", function(d, i) { return i.toString(); })
    .attr("class", "som-cell")
    .attr("stroke", "black")
    .attr("stroke-width", function(d, i) { if (d.maxima) { return 1; } else { return 0; }})
    .text(cellInfo)
//    .popover({placement:'left', trigger:'click', title:'title', content:'content'})
    // .each(function(d, i) {
    // })
//    .on("mouseover", function(){d3.select(this).style("fill", "white");})
  ;
  $("rect")
    .on("mouseover", cellMouseover)
    .on("mouseout", cellMouseout)
    .on("click", cellClick)
  ;
  var mapInfo = "Number of clusters: " + som.numWith;
  mapInfo += ", Items clustered: " + som.totalWith;
  mapInfo += ", Avg items/cluster: " + som.avgNum;
//  mapInfo += ", Number of outliers: " + som.numOutliers;
  mapInfo += ", Max items per cell: " + som.maxCellNum;
  mapInfo += ", Sum Squared Distance: " + som.ssd;
  $("#map-info").html(mapInfo);
//  $("#som-cell").popover({placement:'left', trigger:'hover',title:'hello',content:'content'}).show();
}

function getSOM() {
  $.ajax({
    url: 'som',
    type: 'GET',
    contentType: 'application/json',
    data: {id: g_OpenIndices[0].Id},
    dataType: "json",
    success: drawSOM,
    error: function(xhr, status) { alert("failed to get som, status is " + status); }
  });
}

function recvQueryInfo(results) {
  $('#indexInfo').modal('hide');

  g_OpenIndices.push(results);
  var html = '';
  html += '<p>';
  html += results.Path.split('/').pop();
  html += ' (';
  html += results.NumDocs;
  html += ' items)</p>';
  $('#indexList').append(html);
  getSOM();
}

function openIndex(event) {
  $.ajax({
    url: 'index',
    type: 'POST',
    contentType: 'application/json',
    data: JSON.stringify({Path: $('#indexInfoPath').val()}),
    dataType: "json",
    success: recvQueryInfo,
    error: function(xhr, status) { alert("failed to open index"); }
  });
}

function recvResults(searchResults) {
  var stopTime = jQuery.now();
  var seconds = (stopTime - g_StartSearch) / 1000;
  var html = '<div id="searchResults"><small>';
  html += searchResults.TotalHits;
  html += " items (";
  html += seconds;
  html += "s, Id: ";
  html += searchResults.Id;
  html += ")</small></div>";
  g_CurQueryId = searchResults.Id;
  $('#searchResults').replaceWith(html);

  $('#results').dataTable({
    "sAjaxSource": "dt-results",
    "sServerMethod": "GET",
    "fnServerParams": function(aoData) {  aoData.push({"name":"id", "value":g_CurQueryId });},
    // "aaData":tblData,
    "bFilter": false,
    "bDestroy": true,
    "bProcessing": true,
    "bServerSide": true,
    "bSort": false, // to-do, do sorting on lucene side
    "bStateSave": true,
    "sScrollX": "100%",
    "bScrollCollapse": true,
    "sDom": "<'row'<'span4 offset8'l>r>t<'row'><'span5'i><'span3'p>>",
    "sPaginationType":"bootstrap",
    "aoColumnDefs": [{"sType":"date", "aTargets":[5, 6, 7]}]
  });
}

function submitSearch(event) {
  g_StartSearch = jQuery.now();
  $.ajax({
    url: 'search',
    type: 'GET',
    data: {q: $('#searchBox').val()},
    dataType: "json",
    success: recvResults,
    error: function(xhr, status) { alert("failed to get search results"); }
  });
}

function showDoc(doc) {
  $('#docNameHeader').html('<button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button><h5>' + doc.Path + doc.Name + "</h5>");
  $('#bodyContent').html("<p><pre>" + doc.Body + "</pre></p>");
  $('#docDisplay').modal({'show': true, 'keyboard': true, 'backdrop': false});
}

$(document).ready(function() {
  $('#indexInfoOkButton').click(openIndex);
  $('#searchQuery').submit(submitSearch);
  // $('#drawSOM').click(function(event){ getSOM(); });
  $("table").delegate("tr", "click", function() {
    var idTxt = $("td:first", this).text();
    $.ajax({
      url: 'doc',
      type: 'GET',
      data: {id: idTxt},
      dataType: "json",
      success: showDoc,
      error: function(xhr, status) { alert("failed to retrieve document"); }
    });    
  });
//  $('#drawSOM').popover({placement:'bottom', trigger:'hover', 'title':'Draw!', 'content':'click me!'});
} );

// $(".collapse").collapse()

// $("#som").click(function(event) {
//   alert('hello!');
// });

/*
$('lbtCollapsible').collapse({toggle:true})
*/
