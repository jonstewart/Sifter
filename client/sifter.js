
var g_OpenIndices = [];
var g_StartSearch;
var g_CurQueryId = '';
var g_RegionColors = [];
var g_SOM;
var g_LuminanceCurve;
var g_NumSelected = 0;
var g_SelectedDocs = {};
var g_CurDocIDs = '';

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

function cellQuery(cell, replaceQuery) {
  // var clicked = $(this);
  // var cell = clicked.attr("cell");
  var searchBox = $("#searchBox");
  var newQuery = "cell:\"" + cell + "\"";
  if (!replaceQuery) {
    newQuery += " " + searchBox.val();
  }
  searchBox.val(newQuery);
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

function dimCellToggle() {
  var cellID = $(this).attr("cell");
  var newColor = ($(this).hasClass("active")) ? cellColor(g_SOM.cells[cellID]): "hsl(0, 0%, 93%)";
  var cell  = $("rect[cell='" + cellID + "']");
  cell.css("fill", newColor);
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
      content += "<div><button class=\"btn btn-small btn-link\" type=\"button\" onclick=\"cellQuery(" + cellID + ", true);\" cell=\"" + cellID + "\">List cluster docs</button>";
      content += "<button class=\"btn btn-small btn-link\" type=\"button\" onclick=\"cellQuery(" + cellID + ", false);\" cell=\"" + cellID + "\">Add to query</button></div>";
      content += "<div><button class=\"btn btn-small  dimCell\" data-toggle=\"button\" cell=\"" + cellID + "\">Gray cell</button></div>";
      content += "<div style=\"padding-top: 5px\">Top terms: <em>" + cell.topTerms.join(", ") + "</em></div>"
      content += "<div style=\"padding-top: 5px; padding-bottom: 5px\">Cluster strength (lower is better): " + cell.stdDev + "</div>";
      content += neighborGraph(cell, cellID, x, y);

      me.popover(
        {title: titleText, content: content, container:"body", placement:"bottom", html: true}
      );
    }
  );
  $("body").on("click", ".dimCell", dimCellToggle);

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

function recvIndexInfo(results) {
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
    success: recvIndexInfo,
    error: function(xhr, status) { alert("failed to open index"); }
  });
}

function checkSelected() {
  $('#numSelected').replaceWith("<span id=\"numSelected\" style=\"padding-left: 1em\">" + g_NumSelected + " selected</span>");
  $('#bookmarkBtn').toggleClass("disabled", g_NumSelected == 0);
}

function selectDoc(docID) {
  if (docID in g_SelectedDocs) {
    --g_NumSelected;
    delete g_SelectedDocs[docID];
  }
  else {
    ++g_NumSelected;
    g_SelectedDocs[docID] = true;
  }
  checkSelected();
}

function clearSelected() {
  g_SelectedDocs = {};
  g_NumSelected = 0;
  checkSelected();
}

function recvComments(comments) {
  if (comments == null || typeof comments == "undefined" || typeof comments.length == "undefined") {
    return;
  }
  for (var i = 0; i < comments.length; ++i) {
    var comment = comments[i];
    var docString = comment.Docs;
    if (docString) {
      var docs = docString.split(' ');
      for (var j = 0; j < docs.length; ++j) {
        var doc = docs[j];
        if (doc) {
          var docSpan = $('#' + doc);
          if (docSpan != null) {
            docSpan.text("*" + doc);
          }          
        }
      }
    }
    else {
      console.warn("comment '" + comment + "' did not have Docs string");
    }
  }
}

function makeDocsClicky() {
  $("#results").delegate(".clickable", "click", function() {
    var idTxt = $(this).text().replace("\*", "");
    $.ajax({
      url: 'doc',
      type: 'GET',
      data: {id: g_OpenIndices[0].Id, docid: idTxt},
      dataType: "json",
      success: showDoc,
      error: function(xhr, status) { alert("failed to retrieve document"); }
    });
  });
}

function loadFiles() {
  $('#resultsHeader').html("<tr><th></th><th>ID</th><th>Score</th><th>Name</th><th>Path</th><th>Extension</th><th>Size</th><th>Modified</th><th>Accessed</th><th>Created</th><th>Cell</th><th>Cell Distance</th></tr>");

  $('#results').dataTable({
    "sAjaxSource": "dt-results",
    "sServerMethod": "GET",
    "fnServerParams": function(aoData) { aoData.push({"name":"id", "value":g_CurQueryId }); },
    "fnDrawCallback": function() {
      $.ajax({
        url: 'bookmarks',
        type: 'GET',
        data: {id: g_OpenIndices[0].Id, docs: g_CurDocIDs},
        dataType: "json",
        success: recvComments,
        error: function(xhr, status) { console.warn("failed to request comments"); }
      }); },
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
    "aoColumnDefs": [
      {"sType":"numeric", "aTargets":[0], "sWidth":"2em", "mRender": function(data, type, full) {
        return "<input type=\"checkbox\" value=\"" + full[1] + "\" class=\"docCheck\" " +
               (full[1] in g_SelectedDocs ? "checked": "") +
               "/>";
      }},
      {"aTargets":[1], "mRender": function(data, type, full) {
        g_CurDocIDs += data;
        g_CurDocIDs += " ";
        return "<span id=\"" + data + "\" class=\"clickable\">" + data + "</span>";
      }},
      {"sType":"date", "aTargets":[6, 7, 8]}
    ]
  });
  $("#results").delegate(".docCheck", "click", function() {
    var idTxt = $(this).val();
    selectDoc(idTxt);
  });
  makeDocsClicky();
}

function loadHits() {
  $('#resultsHeader').html("<tr><th>ID</th><th>Score</th><th>Name</th><th>Hit</th><th>Offset</th><th>End</th><th>Path</th><th>Extension</th><th>Size</th><th>Modified</th><th>Accessed</th><th>Created</th><th>Cell</th><th>Cell Distance</th></tr>");

  $('#results').dataTable({
    "sAjaxSource": "dt-hits",
    "sServerMethod": "GET",
    "fnServerParams": function(aoData) { aoData.push({"name":"id", "value":g_CurQueryId }); },
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
    "aoColumnDefs": [
      {"aTargets":[0], "mRender": function(data, type, full) {
        g_CurDocIDs += data;
        g_CurDocIDs += " ";
        return "<span id=\"" + data + "\" class=\"clickable\">" + data + "</span>";
      }},
      // {"aTargets":[3], "mRender": function(data, type, full) {

      // }}
      {"sType":"date", "aTargets":[9, 10, 11]}
    ]
  });
  makeDocsClicky();
}

function recvResults(searchResults) {
  clearSelected();
  g_CurQueryId = searchResults.Id;

  var stopTime = jQuery.now();
  var seconds = (stopTime - g_StartSearch) / 1000;
  var html = '<span id="searchResults" style="padding-left: 1em">';
  html += searchResults.TotalHits;
  html += " items (";
  html += seconds;
  html += "s).</span>";
  $('#searchResults').replaceWith(html);

  $('#downloadBtn').attr("href", "export?id=" + g_CurQueryId);
  $('#downloadBtn').removeClass("disabled");

  g_CurDocIDs = '';

  if ($('#FilesViewBtn').hasClass("active")) {
    loadFiles();
  }
  else {
    loadHits();
  }
}

function submitSearch(event) {
  g_StartSearch = jQuery.now();
  $.ajax({
    url: 'search',
    type: 'GET',
    data: {id: g_OpenIndices[0].Id, q: $('#searchBox').val()},
    dataType: "json",
    success: recvResults,
    error: function(xhr, status) { alert("failed to get search results"); }
  });
}

function showBookmarkDlg() {
  $('#bookmarkDlg').modal('show');
}

function hideBookmarkDlg() {
  $('#bookmarkDlg').modal('hide');
}

function bookmarkSelected() {
  var docs = "";
  for (var doc in g_SelectedDocs) {
    docs += doc;
    docs += " ";
  }
  $.ajax({
    url: 'bookmark?id=' + g_OpenIndices[0].Id,
    type: 'POST',
    contentType: 'application/json',
    data: JSON.stringify({Docs: docs, Comment: $("#commentText").val()}),
    dataType: "json",
    success: hideBookmarkDlg,
    error: function(xhr, status) { alert("failed to create bookmark"); }
  });
}

function showComments(comments) {
  if (comments == null || typeof comments == "undefined" || typeof comments.length == "undefined") {
    return;
  }
  var html = '<div id="bodyComments"><table class="table table-condensed">\n';
  for (var i = 0; i < comments.length; ++i) {
    if (i == 0) {
      html += "<tr><th>Comment</th><th class=\"span3\">Created</th></tr>\n";
    }
    var comment = comments[i];
    html += "<tr><td>";
    html += comment.Comment;
    html += "</td><td>";
    html += (new Date(comment.Created)).toString();
    html += "</td></tr>\n";
  }
  html += "</table><div><h5>Body</h5></div></div>";
  $('#bodyComments').replaceWith(html);
}

function showDoc(doc) {
  $('#docNameHeader').html('<button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button><h5>' + doc.Path + doc.Name + "</h5>");
  $('#bodyContent').text(doc.Body);
  $('#docDisplay').modal({'show': true, 'keyboard': true, 'backdrop': false});
  $('#bodyComments').replaceWith('<div id="bodyComments"/>');
  $.ajax({
    url: 'bookmarks',
    type: 'GET',
    data: {id: g_OpenIndices[0].Id, docs: doc.ID},
    dataType: "json",
    success: showComments,
    error: function(xhr, status) { console.warn("failed to request comments"); }
  });
}

function showRefDate(refDate) {
  $('#refDate').text((new Date(refDate)).toUTCString());
}

$(document).ready(function() {
  $('#indexInfoOkButton').click(openIndex);
  $('#searchQuery').submit(submitSearch);
  $('#bookmarkBtn').click(showBookmarkDlg);
//  $('#downloadBtn').click(downloadResults);
  $('#bookmarkOkButton').click(bookmarkSelected);
  $('#bookmarkDlg').modal({'show': false, 'keyboard': true, 'backdrop': false});
  $('#FilesViewBtn').click(loadFiles);
  $('#HitsViewBtn').click(loadHits);
  $('#FilesViewBtn').button('toggle');
  $.ajax({
    url: 'refdate',
    type: 'GET',
    dataType: 'json',
    success: showRefDate,
    error: function(xhr, status) { console.warn("Failed to request reference date."); }
  });
} );
