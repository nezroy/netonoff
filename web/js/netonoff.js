(function($) {

    var clearlist = function() {
        $(".container .row.devices").remove();
        $("#alldevices .btn-alloff").prop("disabled", true);
        $("#alldevices .btn-allon").prop("disabled", true);
        $("#allspinner").show();
    };

    var dataload;

    var offClick = function(event) {
        var btn = $(event.target);
        if (btn.hasClass("active")) {
            // do nothing
            return;
        }

        var id = btn.parent().data("deviceid");
        clearlist();
        $.post("api/device/" + id + "/disable", null, dataload, 'json');
    };

    var onClick = function(event) {
        var btn = $(event.target);
        if (btn.hasClass("active")) {
            // do nothing
            return;
        }

        var id = btn.parent().data("deviceid");
        clearlist();
        $.post("api/device/" + id + "/enable", null, dataload, 'json');
    };


    dataload = function(data) {
        var devices = data.devices;
        $("#allspinner").hide();
        $("#alldevices .btn-alloff").prop("disabled", false);
        $("#alldevices .btn-allon").prop("disabled", false);

        for (var i = 0; i < devices.length; i++) {
            var dev = devices[i];
            var html = "<div class='row mt-3 devices";
            if (i % 2 == 0) {
                html += " devices-odd";
            }
            html += "'><div class='col-6 align-self-center text-right'><h4>";
            html += dev.name;
            html += "</h4></div><div class='col-6 align-self-center'>";
            html += "<div class='btn-group btn-group-toggle' data-toggle='buttons' data-deviceid='" + dev.id + "'>";
            html += "<label class='btn btn-secondary btn-devon";
            if (dev.enabled) {
                html += " active";
            }
            html += "'><input type='radio' name='enabled' autocomplete='off'";
            if (dev.enabled) {
                html += " checked";
            }
            html += "> On </label>";
            html += "<label class='btn btn-secondary btn-devoff";
            if (!dev.enabled) {
                html += " active";
            }
            html += "'><input type='radio' name='enabled' autocomplete='off'";
            if (!dev.enabled) {
                html += " checked";
            }
            html += "> Off </label>";
            html += "</div></div></div>";

            $('#allspinner').after(html);
        }

        $(".container .row.devices .btn-devoff").click(offClick);
        $(".container .row.devices .btn-devon").click(onClick);
    };

    var disableAll = function() {
        clearlist();
        $.post("api/devices/disable", null, dataload, 'json');
    };
    $('#alldevices .btn-alloff').click(disableAll);

    var enableAll = function() {
        clearlist();
        $.post("api/devices/enable", null, dataload, 'json');
    };
    $('#alldevices .btn-allon').click(enableAll);

    var init = function() {
        clearlist();
        $.getJSON("api/devices/list", dataload);
    };

    $(document).ready(init);

/*
        <div class="row mt-3 devices devices-odd">
            <div class="col-6 align-self-center text-right">
                <h4>Chloe PC</h4>
            </div>
            <div class="col-6 align-self-center">
                <div class="btn-group btn-group-toggle" data-toggle="buttons">
                    <label class="btn btn-secondary btn-devon">
                        <input type="radio" name="enabled" autocomplete="off"> On
                    </label>
                    <label class="btn btn-secondary btn-devoff">
                        <input type="radio" name="enabled" autocomplete="off"> Off
                    </label>
                </div>                
            </div>
        </div>
*/

})(jQuery);
