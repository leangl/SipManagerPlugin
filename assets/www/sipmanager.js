(function(cordova) {
	
	if(!window.plugins) window.plugins = {};
	if (!window.plugins.SipManagerPlugin) {
		window.plugins.SipManagerPlugin = {
			init : function(opts) {
				invoke('init', opts);
			},
			connect : function(opts) {
				invoke('connect', opts);
		    },
		    disconnect : function(opts) {
		    	invoke('disconnect', opts);
		    },
		    makeCall : function(opts) {
		    	invoke('make_call', opts);
		    },
		    endCurrentCall : function(opts) {
		    	invoke('end_call', opts);
		    },
		    takeIncomingCall : function(opts) {
		    	invoke('take_call', opts);            
		    },
		    rejectIncomingCall : function(opts) {
		    	invoke('reject_call', opts);
		    },
		    setSpeakerMode : function(opts) {
		    	invoke('speaker_mode', opts);
		    },
		    listener : {
		    	onConnecting: function() {
		    		console.log('onConnecting');
		    	},
		    	onConnectionSuccess: function() {
		    		console.log('onConnectionSuccess');
		    	},
		    	onConnectionFailed: function() {
		    		console.log('onConnectionFailed');
		    	},
		    	onCallEstablished: function() {
		    		console.log('onCallEstablished');
		    	},
		    	onCallEnded: function() {
		    		console.log('onCallEnded');
		    	},
		    	onIncomingCall: function() {
		    		console.log('onIncomingCall');
		    	}
		    }
		};
		SipManagerPlugin = window.plugins.SipManagerPlugin
	}
	
	function invoke(op, opts) {
		var args = extend(opts);
		console.log(op)
		return cordova.exec(args.success, 
				args.error, 
                'SipManagerPlugin',
                op,
                [ args.data ]);
	}
	
	var defOpts = {
			success: function(data) {
				console.log('success callback: ' + data);
			},
			error: function(data) {
				console.log('error callback: ' + data);
			}
	}
	
	function extend(opts) {
		return $.extend(defOpts, opts ? opts : {});
	}

})(window.cordova);