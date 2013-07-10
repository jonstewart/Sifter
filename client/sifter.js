
var g_OpenIndices = [];
var g_StartSearch;
var g_CurQueryId = '';
var g_RegionColors = [];
var g_SOM;
var g_LuminanceCurve;

function pluralize(num, descriptor) {
  var ret = num;
  ret += " ";
  ret += descriptor;
  if (num != 0) {
    ret += "s";
  }
  return ret;
}

function cellData(n) {
  var a = new Array();
  for (var i = 0; i < n; ++i) {
    a.push({l: 0.1 + (Math.random() * 0.8), s: 0.25 + (Math.random() * 0.5)});
  }
  return a;
}

var CELL_HIGHLIGHT_STROKE_WIDTH = 2;
var CELL_DIMENSION = 20;

function cellQuery(cell) {
  // var clicked = $(this);
  // var cell = clicked.attr("cell");
  var searchBox = $("#searchBox");
  searchBox.val(searchBox.val() + " cell:\"" + cell + "\"");
  $("#searchQuery").submit();
}

function cellMouseover(evt) {
  $(this).attr("stroke-width", CELL_HIGHLIGHT_STROKE_WIDTH);
  $('#cell-info').html($(this).text());
}

function cellMouseout(evt) {
  $(this).attr("stroke-width", 0);
}

function coordinates(cellNum, cols) {
  return {x: cellNum % cols, y: Math.floor(cellNum / cols)};
}

function getHue(cell) {
  var region = cell.region;
  if (0 <= region && region < g_RegionColors.length) {
    return g_RegionColors[region] * 40;
  }
  return 0;
}

function saturation(cell, max) {
  // low stdDev => high saturation
  return (-100 * (cell.stdDev - max)) / max;
}

function luminance(cell, max) {
  if (cell.num < 0.9) {
    return 100.0;
  }
  else {
    return g_LuminanceCurve[cell.num];
  }
}

function determineLuminanceCurve(som) {
  var numVals = [];
  for (var i = 0; i < som.cells.length; ++i) {
    numVals.push(som.cells[i].num);
  }
  numVals.sort(function(a, b) { return a - b; });
  var i = som.numZero;
  while (i + 1 < numVals.length && numVals[i] == numVals[i + 1]) {
    ++i;
  }
  var luminanceMap = {};
  var fraction = (1 / (numVals.length - i)) * 60; // range from 95% - 20%
  var curLum   = 95;
  while (i < numVals.length) {
    luminanceMap[numVals[i]] = curLum;
    curLum -= fraction;
    ++i;
  }
  return luminanceMap;
}

function cellColor(cell) {
  var hsl = "hsl(" + getHue(cell) + ", " + saturation(cell, g_SOM.maxCellStd) + "%, " + luminance(cell, g_SOM.maxCellNum) + "%)";
  return hsl;
}

function cellInfo(cell, i) {
  return "Cell " + i + ". " + pluralize(cell.num, "document") + ", region " + cell.region
    + ". Top terms: " + cell.topTerms.join(", ");
}

function getCellID(x, y) {
  return y * g_SOM.width + x;
}

function getCellTermDiff(som, lhsID, rhsID) {
  if (lhsID == rhsID) {
    return "";
  }
  else if (lhsID < rhsID) {
    return som.cellTermDiffs[lhsID][rhsID.toString()];
  }
  else {
    var term = som.cellTermDiffs[rhsID][lhsID.toString()];
    if (typeof term === 'undefined') {
      return "*undef*";
    }
    if (term.charAt(0) == '-') {
      return term.substring(1);
    }
    else {
      return "-" + term;
    }
  }
}

function neighborGraph(cell, cellID, x, y) {
  if (cell.num == 0) {
    return "";
  }
  var w = 40;
  var h = 40;
  var spacer = 2;

  var svg = "<svg width=\"" + ((w * 3) + (2 * spacer * w)) + "\" height=\"" + ((h * 3) + (2 * spacer * w)) + "\">";

  var maxY = Math.min(g_SOM.height - 1, y + 1);
  var maxX = Math.min(g_SOM.width -1, x + 1);

  for (var curY = Math.max(0, y - 1); curY < Math.min(g_SOM.height, y + 2); ++curY) {
    for (var curX = Math.max(0, x - 1); curX < Math.min(g_SOM.width, x + 2); ++curX) {
      var id = getCellID(curX, curY);
      var curCell = g_SOM.cells[id];
      var topLeftX = ((spacer + 1) * w) * (curX + 1 - x);
      var topLeftY = ((spacer + 1) * h) * (curY + 1 - y);

      if (curCell.num > 0) {
        var color = cellColor(curCell);
        svg += "<rect fill=\"";
        svg += color;
        svg += "\" x=\"";
        svg += topLeftX;
        svg += "\" y=\"";
        svg += topLeftY;
        svg += "\" width=\"";
        svg += w;
        svg += "\" height=\"";
        svg += h;
        if (curX == x && curY == y) {
          svg += "\" stroke=\"black\" stroke-width=\"1";
        }
        svg += "\"/>";
        svg += "<text x=\"";
        svg += topLeftX + (w / 2);
        svg += "\" y=\"";
        svg += topLeftY + (h / 2) + 3; // just a little offset since y for text is bottom
        svg += "\" style=\"font-size: small\" text-anchor=\"middle\">";
        svg += id;
      //   + "<text x=\"15\" y=\"18\" style=\"font-size: x-small\" text-anchor=\"middle\">1430</text>"

        svg += "</text>";
      }
      if (id != cellID) {
        svg += "<text x=\"";
        if (curX < x) {
          svg += (topLeftX + w + ((spacer / 2) * w));
        }
        else if (curX > x) {
          svg += (topLeftX - ((spacer / 2) * w));
        }
        else {
          svg += (topLeftX + (w / 2));
        }
        svg += "\", y=\"";
        if (curY < y) {
          svg += (topLeftY + h + ((spacer / 2) * h));
        }
        else if (curY > y) {
          svg += (topLeftY - ((spacer / 2) * h));
        }
        else {
          svg += (topLeftY + (h / 2));
        }
        svg += "\" style=\"font-size: x-small\" text-anchor=\"middle\">";
        svg += getCellTermDiff(g_SOM, cellID, id);
        svg += "</text>";
      }
    }
  }
  svg += "</svg>";
  return svg;
}

function drawSOM(som) {
//  d3.selection().prototype.popover = $.popover.Constructor();
  g_SOM = som;
  g_RegionColors = som.regionColors;
  g_LuminanceCurve = determineLuminanceCurve(som);

  var rows = som.height;
  var cols = som.width;
  var cellHeight = CELL_DIMENSION;
  var cellWidth = CELL_DIMENSION;
//  var cells = cellData(rows * cols);
  var mapDiv = d3.select("#map");
  var svg = mapDiv.append("svg");
  var rects = svg.selectAll("rect").data(som.cells).enter();
  rects.append("rect")
    .style("fill", cellColor)
    .attr("cell", function(d, i) { return i; })
    .attr("x", function(d, i) { return (i % cols) * cellWidth; })
    .attr("y", function(d, i) { return Math.floor(i / rows) * cellHeight; })
    .attr("width", cellWidth)
    .attr("height", cellHeight)
    .attr("data-title", function(d, i) { return i.toString(); })
    .attr("class", "som-cell")
    .attr("stroke", "black")
    .attr("stroke-width", 0)
    .text(cellInfo)
  ;
  $("rect")
    .on("mouseover", cellMouseover)
    .on("mouseout", cellMouseout)
  ;
  $("rect").each(
    function(d, i) {
      var me = $(this);
      var cellID = me.attr("cell");
      var cell = g_SOM.cells[cellID];
      var x = me.attr("x") / cellWidth;
      var y = me.attr("y") / cellHeight;
      var titleText = "Cell " + cellID + " (" + x + ", " + y + ")";

      var content = "<div>" + pluralize(cell.num, "document") + ", region " + cell.region + "</div>";
      content += "<div><button class=\"btn btn-link queryCluster\" type=\"button\" onclick=\"cellQuery(" + cellID + ");\" cell=\"" + cellID + "\">Add cluster to search query</button></div>";
      content += "<div style=\"padding-top: 5px\">Top terms: <em>" + cell.topTerms.join(", ") + "</em></div>"
      content += "<div style=\"padding-top: 5px; padding-bottom: 5px\">Cluster strength (lower is better): " + cell.stdDev + "</div>";
      content += neighborGraph(cell, cellID, x, y);
      // content += "<svg width=\"210\" height=\"210\">"
      //   + "<rect x=\"0\" y=\"0\" width=\"30\" height=\"30\" fill=\"none\" stroke=\"black\" stroke-width=\"1\"/>"
      //   + "<text x=\"15\" y=\"18\" style=\"font-size: x-small\" text-anchor=\"middle\">1430</text>"
      //   + "<rect x=\"90\" y=\"0\" width=\"30\" height=\"30\" fill=\"none\" stroke=\"black\" stroke-width=\"1\"/>"
      //   + "<rect x=\"180\" y=\"0\" width=\"30\" height=\"30\" fill=\"none\" stroke=\"black\" stroke-width=\"1\"/>"
      //   + "<rect x=\"0\" y=\"90\" width=\"30\" height=\"30\" fill=\"none\" stroke=\"black\" stroke-width=\"1\"/>"
      //   + "<rect x=\"90\" y=\"90\" width=\"30\" height=\"30\" fill=\"none\" stroke=\"black\" stroke-width=\"1\"/>"
      //   + "<rect x=\"180\" y=\"90\" width=\"30\" height=\"30\" fill=\"none\" stroke=\"black\" stroke-width=\"1\"/>"
      //   + "<rect x=\"0\" y=\"180\" width=\"30\" height=\"30\" fill=\"none\" stroke=\"black\" stroke-width=\"1\"/>"
      //   + "<rect x=\"90\" y=\"180\" width=\"30\" height=\"30\" fill=\"none\" stroke=\"black\" stroke-width=\"1\"/>"
      //   + "<rect x=\"180\" y=\"180\" width=\"30\" height=\"30\" fill=\"none\" stroke=\"black\" stroke-width=\"1\"/>"
      //   + "<line x1=\"30\" y1=\"30\" x2=\"90\" y2=\"90\" style=\"stroke:rgb(255,0,0);stroke-width:1\"/>"
      //   + "<line x1=\"105\" y1=\"30\" x2=\"105\" y2=\"90\" style=\"stroke:rgb(255,0,0);stroke-width:1\"/>"
      //   + "<line x1=\"180\" y1=\"30\" x2=\"120\" y2=\"90\" style=\"stroke:rgb(255,0,0);stroke-width:1\"/>"
      //   + "<line x1=\"30\" y1=\"105\" x2=\"90\" y2=\"105\" style=\"stroke:rgb(255,0,0);stroke-width:1\"/>"
      //   + "<line x1=\"180\" y1=\"105\" x2=\"120\" y2=\"105\" style=\"stroke:rgb(255,0,0);stroke-width:1\"/>"
      //   + "<line x1=\"30\" y1=\"180\" x2=\"90\" y2=\"120\" style=\"stroke:rgb(255,0,0);stroke-width:1\"/>"
      //   + "<line x1=\"105\" y1=\"180\" x2=\"105\" y2=\"120\" style=\"stroke:rgb(255,0,0);stroke-width:1\"/>"
      //   + "<line x1=\"180\" y1=\"180\" x2=\"120\" y2=\"120\" style=\"stroke:rgb(255,0,0);stroke-width:1\"/>"
      //   + "</svg>";

      me.popover(
        {title: titleText, content: content, container:"body", placement:"bottom", html: true}
      );
    }
  );

  var mapInfo = "Number of clusters: " + som.numWith;
  mapInfo += ", Items clustered: " + som.totalWith;
  mapInfo += ", Avg items/cluster: " + som.avgNum;
  mapInfo += ", Max items per cell: " + som.maxCellNum;
  mapInfo += ", Num regions: " + som.numMaxima;
  mapInfo += ", Sum Squared Distance: " + som.ssd;
  $("#map-info").html(mapInfo);
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
  g_CurQueryId = searchResults.Id;
  var stopTime = jQuery.now();
  var seconds = (stopTime - g_StartSearch) / 1000;
  var html = '<div id="searchResults"><small>';
  html += searchResults.TotalHits;
  html += " items (";
  html += seconds;
  html += "s). <a href=\"export?id=" + g_CurQueryId + "\">Download (CSV)</a></small></div>";
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
